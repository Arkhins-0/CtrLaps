import "server-only";

import type { SessionUser } from "./auth";
import { q } from "./db";
import { formatIn } from "./time";

/*
 * The coordinator's (and admin's) card on Home: today's sessions and any time changes since yesterday, and the people
 * they look after who need a nudge — invites not accepted, profiles not finished, anyone suspended. Admins see
 * everyone; coordinators everyone but admins and coordinators (as in People).
 */

export type DashboardPerson = { id: string; name: string };
export type Dashboard = {
  sessions: { id: string; name: string; weekendId: string; weekendName: string; startsAt: string; track: string }[];
  changes: string[];
  pending: DashboardPerson[];
  unfinished: DashboardPerson[];
  suspended: DashboardPerson[];
};

const REACH: Record<string, string> = { admin: "true", coordinator: "u.role NOT IN ('admin', 'coordinator')" };

export async function dashboard(user: SessionUser): Promise<Dashboard | null> {
  const reach = REACH[user.role];
  if (!reach) return null;
  const people = (where: string) =>
    q<DashboardPerson>(
      `SELECT u.id, COALESCE(NULLIF(u.name, ''), u.email) AS name FROM users u
       WHERE u.id <> $1 AND ${reach} AND ${where} ORDER BY lower(COALESCE(NULLIF(u.name, ''), u.email)) LIMIT 50`,
      [user.id],
    );
  const [sessions, changes, pending, unfinished, suspended] = await Promise.all([
    // Today in each weekend's own time zone.
    q<{ id: string; name: string; weekend_id: string; weekend_name: string; starts_at: string; timezone: string }>(
      `SELECT s.id, s.name, w.id AS weekend_id, w.name AS weekend_name, s.starts_at, w.timezone
       FROM race_sessions s JOIN race_weekends w ON w.id = s.weekend_id
       WHERE (s.starts_at AT TIME ZONE w.timezone)::date = (now() AT TIME ZONE w.timezone)::date
       ORDER BY s.starts_at`,
    ),
    q<{ detail: { before?: { name?: string; startsAt?: string }; session?: { name?: string; startsAt?: string } } | null; timezone: string | null }>(
      `SELECT a.detail, w.timezone FROM audit_log a LEFT JOIN race_weekends w ON w.id = a.target_id
       WHERE a.action = 'session.updated' AND a.created_at > now() - interval '24 hours'
         AND (a.detail->'before'->>'startsAt') IS DISTINCT FROM (a.detail->'session'->>'startsAt')
       ORDER BY a.id DESC LIMIT 10`,
    ),
    people("u.status = 'pending'"),
    people("u.status = 'active' AND u.profile_completed_at IS NULL"),
    people("u.status = 'suspended'"),
  ]);
  return {
    sessions: sessions.map((s) => ({
      id: s.id,
      name: s.name,
      weekendId: s.weekend_id,
      weekendName: s.weekend_name,
      startsAt: new Date(s.starts_at).toISOString(),
      track: formatIn(s.starts_at, s.timezone, false),
    })),
    changes: changes
      .filter((c) => c.detail?.session?.startsAt)
      .map((c) => `${c.detail!.session!.name ?? "A session"} moved to ${formatIn(c.detail!.session!.startsAt!, c.timezone ?? "UTC")}`),
    pending,
    unfinished,
    suspended,
  };
}
