import "server-only";

import { after } from "next/server";
import { AuthError, type SessionUser } from "./auth";
import { one, q, run, tx } from "./db";
import { sendNotice } from "./email";
import { SITE_URL } from "./config";
import { deliver, preview } from "./notify";
import { pushTo } from "./push";
import { insertMessage, prepareDraft, supportThread, SUPPORT_SENDER, markConversationRead, type Draft, type MessageOut } from "./messages";
import { EMAIL_KIND_LABEL, wantsEmail } from "./emailPrefs";
import { isDeveloper } from "./roles";
import { REOPEN_WINDOW_MS, ticketNumber } from "./supportCategories";

/*
 * Support. Anyone may raise a ticket, signed in or not; developers (admins with is_dev, see roles.ts) answer them.
 * Each ticket has a chat (a conversation of kind 'support') between the person who raised it and every developer.
 * To that person, developers have no name or photo: they are "Support". A ticket stays open until a developer or
 * its owner closes it; its owner may reopen it within two days, a developer at any time.
 */

/* ───────────────────────────── FAQs ──────────────────────────────── */

export type Faq = { id: string; category: string; question: string; answer: string; steps: string[]; position: number };

export async function listFaqs(): Promise<Faq[]> {
  return q<Faq>("SELECT id, category, question, answer, steps, position FROM faqs ORDER BY position, created_at");
}

export type FaqDraft = { category: string; question: string; answer: string; steps: string[] };

export async function addFaq(d: FaqDraft): Promise<Faq> {
  return (await one<Faq>(
    `INSERT INTO faqs (category, question, answer, steps, position)
     VALUES ($1, $2, $3, $4, COALESCE((SELECT max(position) FROM faqs), 0) + 1)
     RETURNING id, category, question, answer, steps, position`,
    [d.category, d.question, d.answer, d.steps],
  ))!;
}

export async function updateFaq(id: string, d: Partial<FaqDraft> & { position?: number }): Promise<Faq | undefined> {
  return one<Faq>(
    `UPDATE faqs SET category = COALESCE($2, category), question = COALESCE($3, question), answer = COALESCE($4, answer),
            steps = COALESCE($5, steps), position = COALESCE($6, position), updated_at = now()
     WHERE id = $1 RETURNING id, category, question, answer, steps, position`,
    [id, d.category ?? null, d.question ?? null, d.answer ?? null, d.steps ?? null, d.position ?? null],
  );
}

export async function deleteFaq(id: string): Promise<boolean> {
  return Boolean(await one("DELETE FROM faqs WHERE id = $1 RETURNING id", [id]));
}

/* ───────────────────────────── Tickets ───────────────────────────── */

type TicketRow = {
  id: string;
  number: number;
  user_id: string | null;
  name: string;
  email: string;
  phone: string | null;
  category: string;
  subject: string;
  details: string;
  status: "open" | "closed";
  conversation_id: string;
  created_at: string;
  closed_at: string | null;
};

export type TicketOut = {
  id: string;
  number: number;
  /** "#0012" */
  label: string;
  category: string;
  subject: string;
  details: string;
  status: "open" | "closed";
  createdAt: string;
  closedAt: string | null;
  /** Who raised it, as they gave it on the form. */
  name: string;
  email: string;
  phone: string | null;
  /** The person's account, when they have one (developers only). */
  userId: string | null;
};

export type TicketListItem = TicketOut & { lastMessageAt: string; lastMessage: string | null; unread: number };

const ticketOut = (t: TicketRow, dev: boolean): TicketOut => ({
  id: t.id,
  number: t.number,
  label: ticketNumber(t.number),
  category: t.category,
  subject: t.subject,
  details: t.details,
  status: t.status,
  createdAt: new Date(t.created_at).toISOString(),
  closedAt: t.closed_at ? new Date(t.closed_at).toISOString() : null,
  name: t.name,
  email: t.email,
  phone: t.phone,
  userId: dev ? t.user_id : null,
});

const COLUMNS = "t.id, t.number, t.user_id, t.name, t.email, t.phone, t.category, t.subject, t.details, t.status, t.conversation_id, t.created_at, t.closed_at";

/** The ids of everyone who answers support now. */
export async function developerIds(): Promise<string[]> {
  return (await q<{ id: string }>("SELECT id FROM users WHERE role = 'admin' AND is_dev AND status = 'active'")).map((r) => r.id);
}

/** A ticket raised before signing in becomes the person's once they sign in with that email. */
async function claimTickets(user: SessionUser): Promise<void> {
  await run("UPDATE support_tickets SET user_id = $1 WHERE user_id IS NULL AND lower(email) = lower($2)", [user.id, user.email]);
}

/** The tickets this person may see: their own, or (a developer) everyone's. `status` narrows them. */
export async function listTickets(user: SessionUser, status: "open" | "closed" | "all"): Promise<TicketListItem[]> {
  const dev = isDeveloper(user);
  if (!dev) await claimTickets(user);
  const rows = await q<TicketRow & { last_at: string | null; last_body: string | null; last_mine: boolean | null; last_event: string | null; unread: string }>(
    `SELECT ${COLUMNS},
            (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = t.conversation_id) AS last_at,
            (SELECT CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted' ELSE m.body END
               FROM messages m WHERE m.conversation_id = t.conversation_id ORDER BY m.created_at DESC LIMIT 1) AS last_body,
            (SELECT m.sender_id = $1 FROM messages m WHERE m.conversation_id = t.conversation_id ORDER BY m.created_at DESC LIMIT 1) AS last_mine,
            (SELECT m.event FROM messages m WHERE m.conversation_id = t.conversation_id ORDER BY m.created_at DESC LIMIT 1) AS last_event,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = t.conversation_id AND r.read_at IS NULL)::text AS unread
     FROM support_tickets t
     WHERE ($2 OR t.user_id = $1) AND ($3 = 'all' OR t.status = $3)
     ORDER BY COALESCE((SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = t.conversation_id), t.created_at) DESC
     LIMIT 300`,
    [user.id, dev, status],
  );
  return rows.map((r) => {
    const text = r.last_event ?? r.last_body;
    return {
      ...ticketOut(r, dev),
      lastMessageAt: new Date(r.last_at ?? r.created_at).toISOString(),
      lastMessage: text ? (r.last_event ? text : `${r.last_mine ? "You" : dev ? r.name : SUPPORT_SENDER.name}: ${text}`).slice(0, 140) : null,
      unread: Number(r.unread),
    };
  });
}

/** One ticket, if this person may see it. */
async function ticketFor(user: SessionUser, id: string): Promise<TicketRow> {
  const dev = isDeveloper(user);
  if (!dev) await claimTickets(user);
  const t = await one<TicketRow>(`SELECT ${COLUMNS} FROM support_tickets t WHERE t.id = $1 AND ($3 OR t.user_id = $2)`, [id, user.id, dev]);
  if (!t) throw new AuthError(404, "No such ticket.");
  return t;
}

export type TicketView = {
  ticket: TicketOut;
  messages: MessageOut[];
  /** The viewer answers as Support (a developer). */
  isDev: boolean;
  canReply: boolean;
  canClose: boolean;
  canReopen: boolean;
  /** Until when its owner may reopen it, once closed. */
  reopenUntil: string | null;
};

/** A ticket and its chat. Reading marks it read, unless `markRead` is false (a phone fetching in the background). */
export async function ticketView(user: SessionUser, id: string, markRead = true): Promise<TicketView> {
  const t = await ticketFor(user, id);
  const dev = isDeveloper(user);
  const messages = await supportThread(user, t.conversation_id, dev);
  if (markRead) await markConversationRead(user.id, t.conversation_id);
  const reopenUntil = t.closed_at ? new Date(new Date(t.closed_at).getTime() + REOPEN_WINDOW_MS) : null;
  return {
    ticket: ticketOut(t, dev),
    messages,
    isDev: dev,
    canReply: t.status === "open",
    canClose: t.status === "open",
    canReopen: t.status === "closed" && (dev || (reopenUntil !== null && reopenUntil.getTime() > Date.now())),
    reopenUntil: reopenUntil?.toISOString() ?? null,
  };
}

export type TicketDraft = { name: string; email: string; phone: string | null; category: string; subject: string; details: string; fileIds: string[] };

/**
 * A new ticket. Its details open the chat as its first message (from the person, when signed in), the developers
 * are told, and the person gets an email with the ticket's number.
 */
export async function raiseTicket(user: SessionUser | null, d: TicketDraft): Promise<{ id: string; number: number; label: string }> {
  const { conversationId, ticket } = await tx(async (c) => {
    const conv = (await c.query<{ id: string }>("INSERT INTO conversations (kind) VALUES ('support') RETURNING id")).rows[0]!;
    const row = (
      await c.query<{ id: string; number: number }>(
        `INSERT INTO support_tickets (user_id, name, email, phone, category, subject, details, conversation_id)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id, number`,
        [user?.id ?? null, d.name, d.email.toLowerCase(), d.phone, d.category, d.subject, d.details, conv.id],
      )
    ).rows[0]!;
    return { conversationId: conv.id, ticket: row };
  });
  const label = ticketNumber(ticket.number);
  const devs = await developerIds();
  // The details, with any screenshots, open the chat (only a signed-in person can have uploaded files).
  if (user) {
    const draft: Draft = { body: d.details, fileIds: d.fileIds };
    const { files } = await prepareDraft(user, conversationId, draft);
    const messageId = await insertMessage(conversationId, user, draft, files);
    await deliver({
      messageId,
      recipientIds: devs.filter((id) => id !== user.id),
      push: { title: `New ticket ${label} · ${d.category}`, body: d.subject, link: `/support/tickets/${ticket.id}`, tag: `t-${ticket.id}` },
    });
  } else if (devs.length > 0) {
    after(async () => {
      await pushTo(devs, { title: `New ticket ${label} · ${d.category}`, body: d.subject, link: `/support/tickets/${ticket.id}`, tag: `t-${ticket.id}` });
    });
  }
  after(async () => {
    const link = `${SITE_URL}/support/tickets/${ticket.id}`;
    const people = await q<{ email: string; name: string | null; mail_token: string }>(
      "SELECT email, name, mail_token FROM users WHERE id = ANY($1::uuid[])",
      [await wantsEmail(devs, "support")],
    );
    if (people.length) await sendNotice(
      people.map((p) => ({ email: p.email, name: p.name, mailToken: p.mail_token })),
      `New ticket ${label}: ${d.subject}`,
      `${label} · ${d.subject}`,
      `${d.name} <${d.email}>${d.phone ? ` · ${d.phone}` : ""}\n${d.category}\n\n${d.details}`,
      link,
      [],
      { eyebrow: "Support", unsubscribe: SUPPORT_MAIL },
    ).catch((error) => console.error("[support] new ticket", error));
    await sendNotice(
      [{ email: d.email, name: d.name }],
      `We got your request (${label})`,
      `Ticket ${label} is open`,
      `Thanks, ${d.name}. Support has your request "${d.subject}" and will reply in the app${user ? "" : " once you sign in with this email"}, and by email.\n\nYour ticket number is ${label}.`,
      link,
      [],
      { eyebrow: "Support" },
    ).catch((error) => console.error("[support] confirmation", error));
  });
  return { id: ticket.id, number: ticket.number, label };
}

/**
 * A message in a ticket's chat, while it is open. From its owner it reaches every developer; from a developer, the
 * owner (as "Support", and by email) and the other developers.
 */
export async function replyToTicket(user: SessionUser, id: string, draft: Draft): Promise<string> {
  const t = await ticketFor(user, id);
  if (t.status !== "open") throw new AuthError(403, "This ticket is closed. Reopen it to write again.");
  if (draft.poll || draft.calendarEvent) throw new AuthError(400, "Polls and events are not for support.");
  const dev = isDeveloper(user);
  const { draft: ready, files } = await prepareDraft(user, t.conversation_id, { ...draft, forwardOf: null });
  const messageId = await insertMessage(t.conversation_id, user, ready, files);
  const text = preview(ready.body, files);
  const label = ticketNumber(t.number);
  const devs = (await developerIds()).filter((x) => x !== user.id);
  const link = `/support/tickets/${t.id}`;
  if (dev) {
    // To the owner it comes from Support; the other developers see who answered.
    if (t.user_id && t.user_id !== user.id) {
      await deliver({
        messageId,
        recipientIds: [t.user_id],
        push: { title: `Support · ${label}`, body: text, link, tag: `t-${t.id}`, popup: { kind: "chat", senderName: SUPPORT_SENDER.name, senderRole: "", senderPhoto: "", text, attach: "", place: label } },
      });
    }
    await deliver({ messageId, recipientIds: devs.filter((x) => x !== t.user_id), push: { title: `${label} · ${user.name || user.email}`, body: text, link, tag: `t-${t.id}` } });
    after(() =>
      raiserMail(t).then((to) => to && sendNotice(
        [to],
        `Support replied to ${label}`,
        `${label} · ${t.subject}`,
        ready.body.trim() || text,
        `${SITE_URL}${link}`,
        [],
        { eyebrow: "Support", sender: { name: SUPPORT_SENDER.name }, unsubscribe: SUPPORT_MAIL },
      )).catch((error) => console.error("[support] reply mail", error)),
    );
  } else {
    await deliver({ messageId, recipientIds: devs, push: { title: `${label} · ${t.name}`, body: text, link, tag: `t-${t.id}` } });
  }
  return messageId;
}

const SUPPORT_MAIL = { kind: "support", label: EMAIL_KIND_LABEL.support.label };

/**
 * Where a ticket's mails go: its email; for someone with an account, only while they keep Support mail on (with their
 * key for "Stop these emails"). Someone without an account always gets them: it is the only way replies reach them.
 */
async function raiserMail(t: TicketRow): Promise<{ email: string; name: string; mailToken?: string } | null> {
  if (!t.user_id) return { email: t.email, name: t.name };
  if ((await wantsEmail([t.user_id], "support")).length === 0) return null;
  const token = (await one<{ mail_token: string }>("SELECT mail_token FROM users WHERE id = $1", [t.user_id]))?.mail_token;
  return { email: t.email, name: t.name, mailToken: token };
}

/** A line in the chat about the ticket itself ("Ticket closed"), not something someone said. */
async function ticketEvent(t: TicketRow, actor: SessionUser, text: string): Promise<void> {
  const row = await one<{ id: string }>(
    "INSERT INTO messages (conversation_id, sender_id, body, event, season_id) VALUES ($1, $2, '', $3, (SELECT id FROM seasons WHERE is_current LIMIT 1)) RETURNING id",
    [t.conversation_id, actor.id, text],
  );
  await run("UPDATE conversations SET last_message_at = now() WHERE id = $1", [t.conversation_id]);
  const others = [...(await developerIds()), ...(t.user_id ? [t.user_id] : [])].filter((x) => x !== actor.id);
  if (row && others.length) await run("INSERT INTO message_recipients (message_id, user_id, read_at) SELECT $1, unnest($2::uuid[]), now() ON CONFLICT DO NOTHING", [row.id, others]);
}

/**
 * Close a ticket (its owner or a developer), or reopen it: its owner within two days of closing, a developer any time.
 */
export async function setTicketStatus(user: SessionUser, id: string, status: "open" | "closed"): Promise<void> {
  const t = await ticketFor(user, id);
  const dev = isDeveloper(user);
  if (t.status === status) return;
  // Who closed or reopened it: the owner by name, a developer only as Support.
  const who = dev ? SUPPORT_SENDER.name : t.name;
  if (status === "closed") {
    await run("UPDATE support_tickets SET status = 'closed', closed_at = now(), closed_by = $2 WHERE id = $1", [t.id, user.id]);
    await ticketEvent(t, user, `${who} closed the ticket`);
    if (dev) {
      after(() =>
        raiserMail(t).then((to) => to && sendNotice(
          [to],
          `${ticketNumber(t.number)} is closed`,
          `${ticketNumber(t.number)} · ${t.subject}`,
          "Support closed this ticket. If it isn't sorted, you can reopen it within 2 days from the ticket's page.",
          `${SITE_URL}/support/tickets/${t.id}`,
          [],
          { eyebrow: "Support", unsubscribe: SUPPORT_MAIL },
        )).catch((error) => console.error("[support] closed mail", error)),
      );
    }
  } else {
    const until = t.closed_at ? new Date(t.closed_at).getTime() + REOPEN_WINDOW_MS : 0;
    if (!dev && until < Date.now()) throw new AuthError(403, "This ticket closed more than 2 days ago. Raise a new one instead.");
    await run("UPDATE support_tickets SET status = 'open', closed_at = NULL, closed_by = NULL WHERE id = $1", [t.id]);
    await ticketEvent(t, user, `${who} reopened the ticket`);
  }
}
