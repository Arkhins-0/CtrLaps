import "server-only";

import type { SessionUser } from "./auth";
import { categoryMemberSql } from "./categoryChannels";
import { categoriesOf } from "./categories";
import { one, q, tx } from "./db";
import { NO_AUTO_EMAIL } from "./roles";
import { currentSeason } from "./seasons";

/*
 * Which emails each account gets. Every kind is on unless the person switched it off; results are for their own
 * race categories unless they chose otherwise (category by category). Not here, and always sent: account and security
 * mails (sign-up, password, email change, role changes), the organisers' Email page, and a ticket's confirmation.
 * Volunteers and security get no automatic email at all (their coordinator forwards what matters).
 */

export const EMAIL_KINDS = ["announcements", "weekend_channels", "category_channels", "results", "chats", "support"] as const;
export type EmailKind = (typeof EMAIL_KINDS)[number];

export const EMAIL_KIND_LABEL: Record<EmailKind, { label: string; hint: string }> = {
  announcements: { label: "Announcements", hint: "Urgent announcements, or ones with files" },
  weekend_channels: { label: "Race weekend channels", hint: "Urgent posts, or ones with files" },
  category_channels: { label: "Category channels", hint: "Urgent posts, or ones with files" },
  results: { label: "Results", hint: "When a session's results are published" },
  chats: { label: "Private chats and groups", hint: "Messages marked urgent" },
  support: { label: "Support", hint: "Replies to your tickets" },
};

export const isEmailKind = (v: unknown): v is EmailKind => EMAIL_KINDS.includes(v as EmailKind);

/** Of these people, the ones who get [kind] by email: active, not volunteers or security, and not switched off. */
export async function wantsEmail(userIds: string[], kind: EmailKind): Promise<string[]> {
  if (userIds.length === 0) return [];
  const rows = await q<{ id: string }>(
    `SELECT u.id FROM users u
     WHERE u.id = ANY($1::uuid[]) AND u.status = 'active' AND NOT (u.role = ANY($3::text[]))
       AND NOT EXISTS (SELECT 1 FROM email_preferences p WHERE p.user_id = u.id AND p.kind = $2 AND NOT p.enabled)`,
    [userIds, kind, NO_AUTO_EMAIL],
  );
  return rows.map((r) => r.id);
}

/** The SQL test "user u gets category $cat's results": their own choice for it, else whether it is one of theirs. */
const followsResultsSql = (u: string, cat: string) => `(
  COALESCE(
    (SELECT s.enabled FROM result_subscriptions s WHERE s.user_id = ${u}.id AND s.category_id = ${cat}),
    ${categoryMemberSql(u, cat)}
  ))`;

/**
 * Who hears about a category's results: everyone active who follows them (by choice, or as one of their categories).
 * `push` for all of them; `email` for those who also kept results mail on (and get automatic email at all).
 */
export async function resultsAudience(categoryId: string): Promise<{ push: string[]; email: string[] }> {
  const rows = await q<{ id: string; mail: boolean }>(
    `SELECT u.id,
            (NOT (u.role = ANY($2::text[]))
             AND NOT EXISTS (SELECT 1 FROM email_preferences p WHERE p.user_id = u.id AND p.kind = 'results' AND NOT p.enabled)) AS mail
     FROM users u WHERE u.status = 'active' AND ${followsResultsSql("u", "$1")}`,
    [categoryId, NO_AUTO_EMAIL],
  );
  return { push: rows.map((r) => r.id), email: rows.filter((r) => r.mail).map((r) => r.id) };
}

export type EmailSettings = {
  /** False for volunteers and security: no automatic email, so nothing to choose. */
  automatic: boolean;
  kinds: { key: EmailKind; label: string; hint: string; on: boolean }[];
  /** This season's categories: whether their results come, and whether that is the default (one of yours). */
  results: { id: string; code: string; name: string; color: string; on: boolean; mine: boolean }[];
};

export async function emailSettings(user: SessionUser): Promise<EmailSettings> {
  const season = await currentSeason();
  const [prefs, cats] = await Promise.all([
    q<{ kind: string; enabled: boolean }>("SELECT kind, enabled FROM email_preferences WHERE user_id = $1", [user.id]),
    categoriesOf([season.id]),
  ]);
  const flags = cats.length
    ? await q<{ id: string; on: boolean; mine: boolean }>(
        `SELECT c.id, ${followsResultsSql("u", "c.id")} AS on, ${categoryMemberSql("u", "c.id")} AS mine
         FROM categories c JOIN users u ON u.id = $1 WHERE c.id = ANY($2::uuid[])`,
        [user.id, cats.map((c) => c.id)],
      )
    : [];
  const off = new Set(prefs.filter((p) => !p.enabled).map((p) => p.kind));
  return {
    automatic: !NO_AUTO_EMAIL.includes(user.role),
    kinds: EMAIL_KINDS.map((k) => ({ key: k, ...EMAIL_KIND_LABEL[k], on: !off.has(k) })),
    results: cats.map((c) => {
      const f = flags.find((x) => x.id === c.id);
      return { id: c.id, code: c.code, name: c.name, color: c.color, on: Boolean(f?.on), mine: Boolean(f?.mine) };
    }),
  };
}

/** Save a person's choices: kinds switched on or off, and categories whose results they do or don't want. */
export async function saveEmailSettings(user: SessionUser, kinds: Partial<Record<EmailKind, boolean>>, results: Record<string, boolean>): Promise<void> {
  const season = await currentSeason();
  const valid = new Set((await categoriesOf([season.id])).map((c) => c.id));
  await tx(async (c) => {
    for (const [kind, on] of Object.entries(kinds)) {
      if (!isEmailKind(kind) || typeof on !== "boolean") continue;
      // On is the default: only an "off" is kept.
      if (on) await c.query("DELETE FROM email_preferences WHERE user_id = $1 AND kind = $2", [user.id, kind]);
      else
        await c.query(
          "INSERT INTO email_preferences (user_id, kind, enabled) VALUES ($1, $2, false) ON CONFLICT (user_id, kind) DO UPDATE SET enabled = false, updated_at = now()",
          [user.id, kind],
        );
    }
    for (const [categoryId, on] of Object.entries(results)) {
      if (!valid.has(categoryId) || typeof on !== "boolean") continue;
      await c.query(
        "INSERT INTO result_subscriptions (user_id, category_id, enabled) VALUES ($1, $2, $3) ON CONFLICT (user_id, category_id) DO UPDATE SET enabled = $3",
        [user.id, categoryId, on],
      );
    }
  });
}

/** The account a mail's "Stop these emails" link names, and that kind switched off for it. */
export async function unsubscribe(mailToken: string, kind: EmailKind): Promise<{ email: string } | null> {
  const user = await one<{ id: string; email: string }>("SELECT id, email FROM users WHERE mail_token = $1::uuid", [mailToken]);
  if (!user) return null;
  await q(
    "INSERT INTO email_preferences (user_id, kind, enabled) VALUES ($1, $2, false) ON CONFLICT (user_id, kind) DO UPDATE SET enabled = false, updated_at = now()",
    [user.id, kind],
  );
  return { email: user.email };
}
