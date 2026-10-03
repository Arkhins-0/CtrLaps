import "server-only";

import { q } from "./db";
import { roleLabel, ROLE_LABEL, ROLES, isRole, type Role } from "./roles";
import { formatIn } from "./time";

/*
 * The Activity log, for developers only: what organisers did, who did it and when, read from audit_log. Each entry is
 * turned into a line a person can read ("Changed a session — Qualifying: Sat 2:00 pm – 3:00 pm → 2:30 pm – 3:30 pm").
 * Private chats and groups are never logged; announcements, emails and channel posts are (with a short preview).
 */

export type ActivityEntry = {
  id: string;
  at: string;
  action: string;
  title: string;
  detail: string | null;
  actor: { id: string; name: string; roleLabel: string } | null;
  target: { id: string; name: string; kind: "person" | "weekend" } | null;
};

/** The groups the log can be narrowed to, by the start of the action's name. */
export const ACTIVITY_KINDS = {
  messages: ["announcement.", "email.sent", "channel.", "category.posted"],
  schedule: ["session.created", "session.updated", "session.deleted", "weekend.", "season."],
  results: ["session.results", "category.scoring", "categories."],
  people: ["user.", "invite.", "developer.", "account.", "team.", "profile.", "password.", "email.changed"],
} as const;
export type ActivityKind = keyof typeof ACTIVITY_KINDS;
export const isActivityKind = (v: unknown): v is ActivityKind => typeof v === "string" && v in ACTIVITY_KINDS;

const TITLE: Record<string, string> = {
  "announcement.sent": "Sent an announcement",
  "email.sent": "Sent an email notice",
  "channel.posted": "Posted in a weekend channel",
  "category.posted": "Posted in a category channel",
  "session.created": "Added a session",
  "session.updated": "Changed a session",
  "session.deleted": "Removed a session",
  "session.results_saved": "Saved results",
  "weekend.created": "Created a race weekend",
  "weekend.updated": "Changed a race weekend",
  "weekend.deleted": "Deleted a race weekend",
  "weekend.managers": "Set a channel's managers",
  "weekend.channel_opened": "Opened a weekend channel",
  "weekend.channel_closed": "Closed a weekend channel",
  "season.created": "Created a season",
  "season.updated": "Changed a season",
  "season.made_current": "Made a season current",
  "season.deleted": "Deleted a season",
  "categories.saved": "Saved a season's categories",
  "category.scoring_saved": "Saved a points table",
  "team.created": "Added a team",
  "team.updated": "Changed a team",
  "team.deleted": "Deleted a team",
  "user.created": "Invited someone",
  "user.promoted": "Gave a role",
  "user.updated": "Changed a person",
  "user.photo": "Changed a photo",
  "user.categories": "Set race categories",
  "user.registered": "Registered",
  "invite.accepted": "Accepted an invite",
  "invite.resent": "Resent an invite",
  "profile.completed": "Completed their profile",
  "profile.edited": "Edited their profile",
  "password.changed": "Changed their password",
  "password.reset": "Reset their password",
  "email.changed": "Changed their email",
  "developer.made": "Gave support access",
  "developer.removed": "Took support access away",
  "account.deletion_requested": "Asked to delete an account",
  "account.deletion_cancelled": "Cancelled an account deletion",
  "account.deleted": "An account was deleted",
};

type Row = {
  id: string;
  created_at: string;
  action: string;
  detail: Record<string, unknown> | null;
  actor_id: string | null;
  actor_name: string | null;
  actor_email: string | null;
  actor_role: string | null;
  actor_dev: boolean | null;
  target_id: string | null;
  target_user: string | null;
  target_weekend: string | null;
  weekend_tz: string | null;
};

const text = (v: unknown): string => (typeof v === "string" ? v : typeof v === "number" ? String(v) : "");
const role = (v: unknown): string => (isRole(v) ? ROLE_LABEL[v as Role] : text(v));

/** A session's times in the track's time zone: "Sat 4 Oct, 2:00 pm – 3:00 pm". */
function times(s: unknown, tz: string): string {
  const o = (s ?? {}) as { startsAt?: string; endsAt?: string };
  if (!o.startsAt || !o.endsAt) return "";
  return `${formatIn(o.startsAt, tz)} – ${formatIn(o.endsAt, tz, false)}`;
}

function describe(r: Row): string | null {
  const d = r.detail ?? {};
  const tz = r.weekend_tz ?? "UTC";
  const urgent = d.urgent ? " · urgent" : "";
  switch (r.action) {
    case "announcement.sent":
    case "email.sent":
      return `To ${text(d.recipients)} ${d.recipients === 1 ? "person" : "people"}${urgent} — ${text(d.subject) || text(d.preview)}`;
    case "channel.posted":
      return `${text(d.channel)}${urgent} — ${text(d.preview)}`;
    case "category.posted":
      return `${text(d.category)}${urgent} — ${text(d.preview)}`;
    case "session.updated": {
      const before = d.before as { name?: string } | undefined;
      const after = d.session as { name?: string } | undefined;
      const name = after?.name ?? before?.name ?? "";
      const was = times(before, tz);
      const now = times(after, tz);
      return `${name}${before?.name && after?.name && before.name !== after.name ? ` (was ${before.name})` : ""}: ${was && was !== now ? `${was} → ` : ""}${now} track time`;
    }
    case "session.created":
      return `${text((d.session as { name?: string })?.name)}: ${times(d.session, tz)} track time`;
    case "session.deleted":
      return text((d.session as { name?: string })?.name) || null;
    case "session.results_saved":
      return d.rows !== undefined ? `${text(d.rows)} rows` : null;
    case "weekend.managers":
      return Array.isArray(d.userIds) ? `${d.userIds.length} ${d.userIds.length === 1 ? "manager" : "managers"}` : null;
    case "user.created":
      return d.role ? `As ${role(d.role)}` : null;
    case "user.promoted":
      return `${role(d.from)} → ${role(d.to)}`;
    case "user.updated": {
      const c = (d.changes ?? {}) as Record<string, unknown>;
      const parts = Object.keys(c).map((k) => (k === "status" ? `status: ${text(c.status)}` : k === "team_name" ? "team" : k === "parent_id" ? "coordinator" : k));
      return parts.length ? `Changed ${parts.join(", ")}` : null;
    }
    case "account.deletion_requested":
      return d.self ? "By themselves" : "On their request";
    default:
      return null;
  }
}

/** Who did it, for the filter: a role, "developer" (an admin who answers support), or "system" (nobody signed in). */
export const ACTIVITY_ROLES = ["developer", ...ROLES, "system"] as const;
export type ActivityRole = (typeof ACTIVITY_ROLES)[number];
export const isActivityRole = (v: unknown): v is ActivityRole => typeof v === "string" && (ACTIVITY_ROLES as readonly string[]).includes(v);

/** How far back: today, the last 7 or 30 days, or everything. */
export const ACTIVITY_PERIODS = { today: "today", "7d": "7 days", "30d": "30 days" } as const;
export type ActivityPeriod = keyof typeof ACTIVITY_PERIODS;
export const isActivityPeriod = (v: unknown): v is ActivityPeriod => typeof v === "string" && v in ACTIVITY_PERIODS;

export type ActivityQuery = {
  /** The last id already shown (the next page continues after it, in the chosen order). */
  before?: string | null;
  kind?: ActivityKind | null;
  /** Words to find: in who did it, who or what it was done to, what was done, and the readable detail (never ids). */
  search?: string | null;
  role?: ActivityRole | null;
  period?: ActivityPeriod | null;
  /** Newest first (the default) or oldest first. */
  oldest?: boolean;
  limit?: number;
};

/** A page of entries, `limit` at a time, filtered, searched and in the chosen order. */
export async function activity(opts: ActivityQuery): Promise<{ entries: ActivityEntry[]; next: string | null }> {
  const limit = Math.min(Math.max(opts.limit ?? 50, 1), 200);
  const patterns = opts.kind ? ACTIVITY_KINDS[opts.kind].map((p) => `${p}%`) : [];
  const words = (opts.search ?? "").trim().slice(0, 100);
  const like = words ? `%${words.replace(/[\\%_]/g, (c) => `\\${c}`)}%` : null;
  // "Changed a session" is a title made here, so a search for its words finds the actions it names.
  const titled = words ? Object.entries(TITLE).filter(([, t]) => t.toLowerCase().includes(words.toLowerCase())).map(([k]) => k) : [];
  const cursor = opts.before && /^\d+$/.test(opts.before) ? opts.before : null;
  const since = opts.period === "today" ? "1 day" : opts.period === "7d" ? "7 days" : opts.period === "30d" ? "30 days" : null;
  const role = opts.role ?? null;
  const rows = await q<Row>(
    `SELECT a.id::text AS id, a.created_at, a.action, a.detail, a.actor_id,
            au.name AS actor_name, au.email AS actor_email, au.role AS actor_role, au.is_dev AS actor_dev,
            a.target_id, COALESCE(NULLIF(tu.name, ''), tu.email) AS target_user, tw.name AS target_weekend,
            tw.timezone AS weekend_tz
     FROM audit_log a
     LEFT JOIN users au ON au.id = a.actor_id
     LEFT JOIN users tu ON tu.id = a.target_id
     LEFT JOIN race_weekends tw ON tw.id = a.target_id
     WHERE ($1::bigint IS NULL OR (CASE WHEN $4 THEN a.id > $1::bigint ELSE a.id < $1::bigint END))
       AND (cardinality($2::text[]) = 0 OR a.action LIKE ANY($2::text[]))
       AND ($5::text IS NULL OR a.action ILIKE $5 OR a.action = ANY($6::text[])
            OR concat_ws(' ', a.detail->>'preview', a.detail->>'subject', a.detail->>'channel', a.detail->>'category',
                         a.detail->'session'->>'name', a.detail->'before'->>'name', a.detail->>'role', a.detail->>'from',
                         a.detail->>'to', a.detail->'changes'->>'status', a.detail->'changes'->>'name') ILIKE $5
            OR au.name ILIKE $5 OR au.email ILIKE $5 OR tu.name ILIKE $5 OR tu.email ILIKE $5 OR tw.name ILIKE $5)
       AND ($7::text IS NULL
            OR ($7 = 'system' AND a.actor_id IS NULL)
            OR ($7 = 'developer' AND au.role = 'admin' AND au.is_dev)
            OR ($7 = 'admin' AND au.role = 'admin' AND NOT au.is_dev)
            OR ($7 NOT IN ('system', 'developer', 'admin') AND au.role = $7))
       AND ($8::interval IS NULL OR a.created_at >= now() - $8::interval)
     ORDER BY CASE WHEN $4 THEN a.id END ASC, CASE WHEN NOT $4 THEN a.id END DESC
     LIMIT $3`,
    [cursor, patterns, limit + 1, Boolean(opts.oldest), like, titled, role, since],
  );
  const page = rows.slice(0, limit);
  return {
    entries: page.map((r) => ({
      id: r.id,
      at: new Date(r.created_at).toISOString(),
      action: r.action,
      title: TITLE[r.action] ?? r.action,
      detail: describe(r),
      actor: r.actor_id
        ? {
            id: r.actor_id,
            name: r.actor_name || r.actor_email || "Someone",
            roleLabel: isRole(r.actor_role) ? roleLabel(r.actor_role, r.actor_dev) : "",
          }
        : null,
      target: r.target_user
        ? { id: r.target_id!, name: r.target_user, kind: "person" }
        : r.target_weekend
          ? { id: r.target_id!, name: r.target_weekend, kind: "weekend" }
          : null,
    })),
    next: rows.length > limit ? page[page.length - 1].id : null,
  };
}
