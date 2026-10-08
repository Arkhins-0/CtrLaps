import "server-only";
import { one } from "./db";
import { AuthError } from "./auth";

/*
 * How fast one person may post, counted in the database (no memory to lose between Vercel's instances), after
 * Arkhime's: a send past the limit is turned away with 429 and a plain message. Generous enough that nobody
 * working normally meets it; it stops a stuck loop or a script from flooding everyone's phones and inboxes.
 */

/** Sends in a minute: chats and groups (photos sent together count once), posts that reach many, emails. */
const PER_MINUTE = { chat: 60, post: 10, email: 5 } as const;
/** Tickets a signed-in person may raise in ten minutes (signed out, sign-in's attempt limit applies). */
const TICKETS_PER_10_MIN = 5;

export type Pace = keyof typeof PER_MINUTE | "ticket";

const TOO_FAST: Record<Pace, string> = {
  chat: "You're sending very fast. Wait a moment, then try again.",
  post: "That's a lot of posts in a minute. Wait a moment, then try again.",
  email: "That's a lot of emails in a minute. Wait a moment, then try again.",
  ticket: "You've raised several tickets just now. Wait a few minutes, or add to one you raised.",
};

/** Turns the send away (429) when [userId] has already sent as many of this kind as the limit allows. */
export async function checkPace(userId: string, pace: Pace): Promise<void> {
  const row = await one<{ n: number }>(
    pace === "chat"
      ? `SELECT count(DISTINCT coalesce(m.batch_id, m.id::text))::int AS n FROM messages m JOIN conversations c ON c.id = m.conversation_id
         WHERE m.sender_id = $1 AND m.created_at > now() - interval '1 minute' AND c.kind IN ('direct', 'group', 'support')`
      : pace === "post"
        ? `SELECT count(*)::int AS n FROM messages m LEFT JOIN conversations c ON c.id = m.conversation_id
           WHERE m.sender_id = $1 AND m.created_at > now() - interval '1 minute' AND (m.conversation_id IS NULL OR c.kind IN ('channel', 'category'))`
        : pace === "email"
          ? `SELECT count(*)::int AS n FROM audit_log WHERE actor_id = $1 AND action = 'email.sent' AND created_at > now() - interval '1 minute'`
          : `SELECT count(*)::int AS n FROM support_tickets WHERE user_id = $1 AND created_at > now() - interval '10 minutes'`,
    [userId],
  );
  const limit = pace === "ticket" ? TICKETS_PER_10_MIN : PER_MINUTE[pace];
  if ((row?.n ?? 0) >= limit) throw new AuthError(429, TOO_FAST[pace]);
}

/**
 * The same post from the same person moments ago (a second tap, or a send tried again after its answer was lost):
 * its id, so the caller answers with it and nobody is told twice. Same place (a channel, or for announcements the
 * very same people), same words, same first file, not deleted, within a minute.
 */
export async function samePostJustNow(
  senderId: string,
  where: { conversationId: string } | { recipientIds: string[] },
  body: string,
  firstFileId: string | null,
): Promise<string | null> {
  const row =
    "conversationId" in where
      ? await one<{ id: string }>(
          `SELECT id FROM messages WHERE sender_id = $1 AND conversation_id = $2 AND body = $3 AND file_id IS NOT DISTINCT FROM $4
             AND deleted_at IS NULL AND created_at > now() - interval '1 minute' ORDER BY created_at DESC LIMIT 1`,
          [senderId, where.conversationId, body, firstFileId],
        )
      : await one<{ id: string }>(
          `SELECT m.id FROM messages m WHERE m.sender_id = $1 AND m.conversation_id IS NULL AND m.body = $2 AND m.file_id IS NOT DISTINCT FROM $3
             AND m.deleted_at IS NULL AND m.created_at > now() - interval '1 minute'
             AND (SELECT array_agg(r.user_id ORDER BY r.user_id) FROM message_recipients r WHERE r.message_id = m.id) = $4::uuid[]
           ORDER BY m.created_at DESC LIMIT 1`,
          [senderId, body, firstFileId, [...where.recipientIds].sort()],
        );
  return row?.id ?? null;
}
