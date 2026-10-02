import "server-only";

import { env, isEmailConfigured } from "./env";
import { APP_NAME, SITE_URL } from "./config";

/*
 * Transactional email through Brevo's HTTP API. One call can carry many
 * recipients as separate "message versions", so a race-wide notice is one
 * request, not hundreds — and nobody sees anyone else's address.
 */

/** `mailToken`: the account's key for its "Stop these emails" link (mails of a kind people can switch off). */
export type Recipient = { email: string; name?: string | null; mailToken?: string | null };

/** A file carried by the mail itself: its name (with an extension Brevo accepts) and its bytes in base64. */
export type Attachment = { name: string; content: string };

const BREVO_URL = "https://api.brevo.com/v3/smtp/email";
const VERSIONS_PER_CALL = 500;

export async function sendEmail(to: Recipient[], subject: string, html: string, text?: string, attachments: Attachment[] = []): Promise<void> {
  const recipients = to.filter((r) => r.email);
  if (recipients.length === 0) return;
  if (!isEmailConfigured()) {
    console.warn(`[email] not configured; would send "${subject}" to ${recipients.length} recipient(s)`);
    return;
  }
  if (env.email.provider !== "brevo") throw new Error(`Unsupported EMAIL_PROVIDER: ${env.email.provider}`);

  for (let i = 0; i < recipients.length; i += VERSIONS_PER_CALL) {
    const chunk = recipients.slice(i, i + VERSIONS_PER_CALL);
    const body = {
      sender: { email: env.email.from, name: env.email.fromName },
      subject,
      htmlContent: html,
      textContent: text ?? stripHtml(html),
      // Brevo needs a top-level `to` even when versions carry their own.
      to: [{ email: chunk[0].email, name: chunk[0].name || undefined }],
      // Each person's own values for the mail's {{params.…}} (their "Stop these emails" key).
      messageVersions: chunk.map((r) => ({ to: [{ email: r.email, name: r.name || undefined }], ...(r.mailToken ? { params: { mailToken: r.mailToken } } : {}) })),
      ...(attachments.length ? { attachment: attachments } : {}),
    };
    const response = await fetch(BREVO_URL, {
      method: "POST",
      headers: { "api-key": env.email.brevoApiKey, "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      throw new Error(`Brevo ${response.status}: ${detail.slice(0, 300)}`);
    }
  }
}

function stripHtml(html: string): string {
  return html
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

export function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/** What sets a mail apart: a label over the title, who it is from, and the text shown in the inbox list. */
export type MailExtras = {
  /** A small label above the title: "Announcement", "Channel", "Account"… */
  eyebrow?: string;
  /** Red label and a red rule for something urgent. */
  urgent?: boolean;
  /** Who sent the message, shown as a card: their name, role and where they said it. */
  sender?: { name: string; role?: string; place?: string };
  /** The first line an inbox shows beside the subject. */
  preheader?: string;
  /** Names of the files attached to the mail. */
  files?: string[];
  /**
   * A kind people can switch off (emailPrefs.ts): the footer gets "Stop these emails" (one click, with each
   * person's key filled in by Brevo from {{params.mailToken}}) and "Manage email". Only when every recipient has a key.
   */
  unsubscribe?: { kind: string; label: string };
};

const GOLD = "#FFD100";
const INK = "#0B0B0C";
const MUTED = "#6B6B73";
const LINE = "#E7E7EA";
const RED = "#E5484D";

/** Two rows of chequered squares under the header: the finish-line flag. */
function chequer(): string {
  const row = (odd: boolean) =>
    Array.from({ length: 30 }, (_, i) => `<td height="6" style="background:${(i % 2 === 0) === odd ? GOLD : INK};font-size:0;line-height:0">&nbsp;</td>`).join("");
  return `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="table-layout:fixed"><tr>${row(true)}</tr><tr>${row(false)}</tr></table>`;
}

function initials(name: string): string {
  const letters = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join("");
  return escapeHtml(letters || "?");
}

/**
 * The one layout every mail uses: a dark header with the logo and a chequered strip, an optional label and sender
 * card, the words, a gold button (with the link written out under it), attached files, and a footer saying why it came.
 * Tables and inline styles only, so Gmail, Outlook and phone mail apps show it the same.
 */
export function layout(title: string, bodyHtml: string, button?: { label: string; url: string }, extras: MailExtras = {}): string {
  const accent = extras.urgent ? RED : GOLD;
  const eyebrow = extras.urgent ? `Urgent${extras.eyebrow ? ` · ${extras.eyebrow}` : ""}` : extras.eyebrow;
  const host = SITE_URL.replace(/^https?:\/\//, "");
  // From the settings (POWERED_BY_NAME, POWERED_BY_DOMAIN, MAIN_DOMAIN); a blank one is left out.
  const { mainDomain, poweredByName, poweredByDomain } = env.brand;
  const byName = poweredByName || poweredByDomain;
  const poweredBy = byName
    ? `&nbsp;·&nbsp; Powered by ${poweredByDomain ? `<a href="https://${escapeHtml(poweredByDomain)}" style="color:${MUTED}">${escapeHtml(byName)}</a>` : escapeHtml(byName)}`
    : "";
  const label = eyebrow
    ? `<p style="margin:0 0 12px"><span style="display:inline-block;background:${extras.urgent ? "#FDECEC" : "#FFF6CC"};color:${extras.urgent ? RED : "#7A5E00"};font-size:11px;font-weight:700;letter-spacing:1.2px;text-transform:uppercase;padding:5px 10px;border-radius:999px">${escapeHtml(eyebrow)}</span></p>`
    : "";
  const sender = extras.sender
    ? `<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 16px"><tr>
  <td style="vertical-align:middle;padding-right:12px"><div style="width:40px;height:40px;border-radius:20px;background:${INK};color:${GOLD};font-weight:700;font-size:15px;line-height:40px;text-align:center">${initials(extras.sender.name)}</div></td>
  <td style="vertical-align:middle">
    <div style="font-size:15px;font-weight:600;color:${INK}">${escapeHtml(extras.sender.name)}</div>
    <div style="font-size:13px;color:${MUTED}">${escapeHtml([extras.sender.role, extras.sender.place].filter(Boolean).join(" · "))}</div>
  </td></tr></table>`
    : "";
  const files = extras.files?.length
    ? `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:16px 0 0;border:1px solid ${LINE};border-radius:10px">
${extras.files.map((f, i) => `<tr><td style="padding:10px 14px;font-size:13px;color:#26262B;${i ? `border-top:1px solid ${LINE}` : ""}">&#128206;&nbsp; ${escapeHtml(f)}</td></tr>`).join("")}
</table><p style="margin:6px 0 0;font-size:12px;color:${MUTED}">Attached to this email.</p>`
    : "";
  const action = button
    ? `<table role="presentation" cellpadding="0" cellspacing="0" style="margin:26px 0 10px"><tr><td style="background:${GOLD};border-radius:999px">
<a href="${button.url}" style="display:inline-block;padding:13px 26px;color:${INK};font-size:15px;font-weight:700;text-decoration:none">${escapeHtml(button.label)} &rarr;</a>
</td></tr></table>
<p style="margin:0 0 6px;font-size:12px;color:${MUTED}">Or paste this link into your browser:</p>
<p style="margin:0;font-size:12px;word-break:break-all"><a href="${button.url}" style="color:${MUTED}">${escapeHtml(button.url)}</a></p>`
    : "";
  const words = `<div style="font-size:15px;line-height:1.6;color:#26262B;white-space:pre-wrap;${extras.sender ? `background:#F7F7F8;border-left:4px solid ${accent};border-radius:10px;padding:16px 18px` : ""}">${bodyHtml}</div>`;

  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><meta name="supported-color-schemes" content="light"><title>${escapeHtml(title)}</title></head>
<body style="margin:0;padding:0;background:#EFEFF1;font-family:Inter,'Segoe UI',Helvetica,Arial,sans-serif;color:${INK}">
${extras.preheader ? `<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent">${escapeHtml(extras.preheader)}</div>` : ""}
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#EFEFF1;padding:28px 12px">
<tr><td align="center">
<table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#FFFFFF;border-radius:18px;overflow:hidden">
<tr><td style="background:${INK};padding:20px 28px">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>
    <td style="padding-right:12px;vertical-align:middle;width:40px"><img src="${SITE_URL}/logo.png" width="40" height="40" alt="" style="display:block;border:0;border-radius:10px"></td>
    <td style="vertical-align:middle;color:${GOLD};font-weight:800;font-size:20px;letter-spacing:1px">${APP_NAME}</td>
    ${mainDomain ? `<td align="right" style="vertical-align:middle;text-align:right;font-size:13px;font-weight:600;letter-spacing:0.5px"><a href="https://${escapeHtml(mainDomain)}" style="color:#FFFFFF;text-decoration:none">${escapeHtml(mainDomain)}</a></td>` : ""}
  </tr></table>
</td></tr>
<tr><td style="font-size:0;line-height:0">${chequer()}</td></tr>
<tr><td style="padding:30px 32px 8px">
${label}
<h1 style="margin:0 0 18px;font-size:22px;line-height:1.3;font-weight:700;color:${INK}">${escapeHtml(title)}</h1>
${sender}
${words}
${files}
${action}
</td></tr>
<tr><td style="padding:24px 32px 28px">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border-top:1px solid ${LINE}"><tr><td style="padding-top:16px;font-size:12px;line-height:1.6;color:${MUTED}">
    You are getting this because you have a ${APP_NAME} account.${
      extras.unsubscribe
        ? ` <a href="${SITE_URL}/email/unsubscribe?u={{params.mailToken}}&amp;k=${encodeURIComponent(extras.unsubscribe.kind)}" style="color:${MUTED}">Stop ${escapeHtml(extras.unsubscribe.label.toLowerCase())} emails</a> &nbsp;·&nbsp; <a href="${SITE_URL}/account#email" style="color:${MUTED}">Manage email</a>`
        : ""
    }<br>
    <a href="${SITE_URL}" style="color:${INK};font-weight:600;text-decoration:none">${escapeHtml(host)}</a>
    ${poweredBy}
  </td></tr></table>
</td></tr>
</table>
</td></tr></table></body></html>`;
}

/* ───────────────────────────── The mails ─────────────────────────── */

export async function sendInvite(to: Recipient, token: string, invitedBy: string, roleLabel: string) {
  const url = `${SITE_URL}/invite/${token}`;
  await sendEmail(
    [to],
    `Your ${APP_NAME} account`,
    layout(
      `You have been added to ${APP_NAME}`,
      `${escapeHtml(invitedBy)} created a ${escapeHtml(roleLabel)} account for you.<br><br>Open the link to choose a password and set up your profile. If the ${APP_NAME} app is installed it opens there; otherwise the website does. The link works for 7 days.`,
      { label: "Set up my account", url },
      { eyebrow: "Invitation", preheader: `${invitedBy} added you as a ${roleLabel}.` },
    ),
  );
}

export async function sendSignup(email: string, token: string) {
  const url = `${SITE_URL}/register/${token}`;
  await sendEmail(
    [{ email }],
    `Confirm your email for ${APP_NAME}`,
    layout(
      "Confirm your email",
      `Open the link to confirm this is your email, choose a password and create your ${APP_NAME} account. It works for 24 hours. If you did not ask for this, ignore this mail.`,
      { label: "Create my account", url },
      { eyebrow: "Account", preheader: "One step left: confirm your email." },
    ),
  );
}

/** Someone tried to register with an email that already has an account. */
export async function sendAlreadyRegistered(email: string) {
  await sendEmail(
    [{ email }],
    `You already have a ${APP_NAME} account`,
    layout(
      "You already have an account",
      `Someone asked to register this email, but it already has a ${APP_NAME} account. Sign in, or choose a new password if you have forgotten it. If this was not you, ignore this mail.`,
      { label: "Choose a new password", url: `${SITE_URL}/forgot` },
      { eyebrow: "Account" },
    ),
  );
}

export async function sendEmailChange(newEmail: string, token: string) {
  const url = `${SITE_URL}/confirm-email/${token}`;
  await sendEmail(
    [{ email: newEmail }],
    `Confirm your new email for ${APP_NAME}`,
    layout(
      "Confirm your new email",
      `Open the link to make this the email of your ${APP_NAME} account. It works for 24 hours. Until then your old email stays. If you did not ask for this, ignore this mail.`,
      { label: "Confirm this email", url },
      { eyebrow: "Account" },
    ),
  );
}

/** Told to the old address once the change is made, so a change nobody asked for does not go unnoticed. */
export async function sendEmailChanged(oldEmail: string, newEmail: string) {
  await sendEmail(
    [{ email: oldEmail }],
    `Your ${APP_NAME} email was changed`,
    layout(
      "Your email was changed",
      `Your ${APP_NAME} account now uses ${escapeHtml(newEmail)}. If you did not do this, contact the organisers straight away.`,
      undefined,
      { eyebrow: "Security" },
    ),
  );
}

export async function sendReset(to: Recipient, token: string) {
  const url = `${SITE_URL}/reset/${token}`;
  await sendEmail(
    [to],
    `Reset your ${APP_NAME} password`,
    layout(
      "Reset your password",
      `Open the link to choose a new password. It works for 2 hours. If you did not ask for this, ignore this mail.`,
      { label: "Choose a new password", url },
      { eyebrow: "Security", preheader: "The link works for 2 hours." },
    ),
  );
}

export async function sendNotice(
  to: Recipient[],
  subject: string,
  title: string,
  body: string,
  link?: string,
  attachments: Attachment[] = [],
  extras: MailExtras = {},
) {
  // "Stop these emails" needs each person's key; without one for everyone, the footer leaves it out.
  const unsubscribe = extras.unsubscribe && to.length > 0 && to.every((r) => r.mailToken) ? extras.unsubscribe : undefined;
  await sendEmail(
    to,
    subject,
    layout(title, escapeHtml(body), link ? { label: `Open in ${APP_NAME}`, url: link } : undefined, {
      preheader: body.replace(/\s+/g, " ").slice(0, 120),
      files: attachments.map((a) => a.name),
      ...extras,
      unsubscribe,
    }),
    undefined,
    attachments,
  );
}

/* ───────────────────────────── Results ───────────────────────────── */

export type ResultsMail = {
  /** "LGB F4 · Race 1 results" */
  title: string;
  /** "Round 2 · Kari Motor Speedway · Sun 12 Oct, 3:30 pm" */
  where: string;
  rows: { position: number | null; status: string; carNumber: string; driverName: string; teamName: string | null; points: number; pole: boolean; fastestLap: boolean }[];
  /** The category's leaders after it: "F4 championship after Round 2". */
  championship: { heading: string; leaders: { name: string; points: number }[] } | null;
  /** Points are shown only for a session that scores. */
  showPoints: boolean;
  link: string;
};

/**
 * A session's results to the people who follow them: the classification (place, "Name (#car)", team, pole and
 * fastest-lap tags, points), the championship's top three, and a button to the full results.
 */
export async function sendResults(to: Recipient[], r: ResultsMail) {
  const status: Record<string, string> = { dnf: "DNF", dns: "DNS", dsq: "DSQ" };
  const cell = (i: number) => (i ? `border-top:1px solid ${LINE};` : "");
  const badge = (row: ResultsMail["rows"][number]) => {
    const out = row.status !== "finished";
    const podium = !out && row.position !== null && row.position <= 3;
    return `<span style="display:inline-block;min-width:26px;padding:3px 6px;border-radius:7px;background:${out ? "#FDECEC" : podium ? GOLD : "#F0F0F2"};color:${out ? RED : INK};font-weight:700;font-size:12px;text-align:center">${out ? status[row.status] ?? "–" : row.position ?? "–"}</span>`;
  };
  const tag = (t: string, c: string) =>
    `<span style="display:inline-block;margin-left:6px;padding:1px 7px;border:1px solid ${c};border-radius:999px;font-size:10px;font-weight:700;color:${c};vertical-align:middle">${t}</span>`;
  const rows = r.rows
    .map(
      (row, i) =>
        `<tr><td width="44" style="width:44px;padding:9px 0 9px 8px;${cell(i)}">${badge(row)}</td>` +
        `<td style="padding:9px 8px;${cell(i)}"><div style="font-size:14px;font-weight:600;color:${INK}">${escapeHtml(row.driverName)}${row.carNumber ? ` <span style="font-weight:400;color:${MUTED}">(#${escapeHtml(row.carNumber)})</span>` : ""}${row.pole ? tag("POLE", MUTED) : ""}${row.fastestLap ? tag("FASTEST LAP", "#7A5E00") : ""}</div>` +
        `${row.teamName ? `<div style="font-size:12px;color:${MUTED}">${escapeHtml(row.teamName)}</div>` : ""}</td>` +
        (r.showPoints ? `<td align="right" style="padding:9px 8px;font-size:15px;font-weight:700;color:${INK};${cell(i)}">${row.points || ""}</td>` : "") +
        `</tr>`,
    )
    .join("");
  const table =
    `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid ${LINE};border-radius:12px;border-collapse:separate;white-space:normal">` +
    `<tr><td colspan="2" style="padding:10px 8px;font-size:11px;font-weight:700;letter-spacing:1px;color:${MUTED}">RESULT</td>${r.showPoints ? `<td align="right" style="padding:10px 8px;font-size:11px;font-weight:700;letter-spacing:1px;color:${MUTED}">PTS</td>` : ""}</tr>` +
    rows +
    `</table>`;
  const champ = r.championship?.leaders.length
    ? `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin-top:18px;background:#F7F7F8;border-radius:12px;white-space:normal"><tr><td style="padding:12px 14px">` +
      `<div style="font-size:11px;font-weight:700;letter-spacing:1px;color:${MUTED};margin-bottom:6px">${escapeHtml(r.championship.heading.toUpperCase())}</div>` +
      r.championship.leaders
        .map((l, i) => `<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td style="font-size:14px;color:${INK};padding:3px 0"><b>${i + 1}.</b> ${escapeHtml(l.name)}</td><td align="right" style="font-size:14px;font-weight:700;color:${INK}">${l.points}</td></tr></table>`)
        .join("") +
      `</td></tr></table>`
    : "";
  const intro = `<div style="white-space:normal;margin-bottom:14px;color:#26262B">${escapeHtml(r.where)}</div>`;
  const podium = r.rows.filter((x) => x.status === "finished" && x.position !== null).slice(0, 3).map((x) => `${x.position}. ${x.driverName}`).join(" · ");
  const unsubscribe = to.length > 0 && to.every((x) => x.mailToken) ? { kind: "results", label: "Results" } : undefined;
  await sendEmail(
    to,
    r.title,
    layout(r.title, intro + table + champ, { label: "See the full results", url: r.link }, { eyebrow: "Results", preheader: podium, unsubscribe }),
  );
}

