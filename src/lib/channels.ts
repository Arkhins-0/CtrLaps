import "server-only";

import { AuthError, type SessionUser } from "./auth";
import { q, run, tx } from "./db";
import { personCard, type PersonCard, type PersonRow } from "./messages";
import { listSeasons, LIVE_SEASON } from "./seasons";

/*
 * The channels page: every race weekend's channel, season by season, the
 * current season first. Admins post in every channel; an admin names coordinators as a weekend's (or a category's)
 * channel managers, who post there. Anyone can mute a channel's notifications (push only).
 */

export type ChannelManager = PersonCard;

export type ChannelWeekend = {
  id: string;
  name: string;
  startsOn: string;
  endsOn: string;
  channelOpen: boolean;
  unread: number;
  lastMessageAt: string | null;
  lastMessage: string | null;
  managers: ChannelManager[];
  /** This person muted its notifications. */
  muted: boolean;
};

export type ChannelSeason = { id: string; name: string; current: boolean; status: "active" | "archived"; weekends: ChannelWeekend[] };

export async function listChannels(user: SessionUser): Promise<ChannelSeason[]> {
  const seasons = await listSeasons();
  const [weekends, managers] = await Promise.all([
    q<{
      id: string;
      name: string;
      season_id: string | null;
      starts_on: string;
      ends_on: string;
      channel_open: boolean;
      unread: string;
      last_at: string | null;
      last_body: string | null;
      muted: boolean;
    }>(
      `SELECT w.id, w.name, w.season_id, w.starts_on::text AS starts_on, w.ends_on::text AS ends_on,
              EXISTS (SELECT 1 FROM channel_mutes mu WHERE mu.user_id = $1 AND mu.conversation_id = c.id) AS muted,
              (w.channel_open AND COALESCE(s.status, 'active') = 'active') AS channel_open,
              COALESCE((SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
                        WHERE m.conversation_id = c.id AND r.read_at IS NULL AND ${LIVE_SEASON("m")}), 0)::text AS unread,
              (SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_at,
              (SELECT COALESCE(NULLIF(u.name, ''), u.email, 'Someone') || ': ' ||
                      CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted'
                           WHEN m.body <> '' THEN m.body
                           ELSE 'Document' END
                 FROM messages m LEFT JOIN users u ON u.id = m.sender_id
                 WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_body
       FROM race_weekends w
       LEFT JOIN seasons s ON s.id = w.season_id
       LEFT JOIN conversations c ON c.kind = 'channel' AND c.weekend_id = w.id
       ORDER BY w.starts_on DESC`,
      [user.id],
    ),
    q<PersonRow & { weekend_id: string }>(
      `SELECT cm.weekend_id, u.id, u.name, u.email, u.role, u.photo_key, u.is_dev
       FROM channel_managers cm JOIN users u ON u.id = cm.user_id ORDER BY u.name NULLS LAST, u.email`,
    ),
  ]);
  const byWeekend = new Map<string, ChannelManager[]>();
  for (const m of managers) byWeekend.set(m.weekend_id, [...(byWeekend.get(m.weekend_id) ?? []), personCard(m)]);
  const row = (w: (typeof weekends)[number]): ChannelWeekend => ({
    id: w.id,
    name: w.name,
    startsOn: w.starts_on,
    endsOn: w.ends_on,
    channelOpen: w.channel_open,
    unread: Number(w.unread),
    lastMessageAt: w.last_at ? new Date(w.last_at).toISOString() : null,
    lastMessage: w.last_body ? (w.last_body.length > 140 ? `${w.last_body.slice(0, 137)}…` : w.last_body) : null,
    managers: byWeekend.get(w.id) ?? [],
    muted: w.muted,
  });
  // The current season first, then the rest newest first; a weekend without a season goes with the current one.
  const current = seasons.find((s) => s.current);
  const ordered = [...seasons].sort((a, b) => Number(b.current) - Number(a.current) || b.startsOn.localeCompare(a.startsOn));
  return ordered.map((s) => ({
    id: s.id,
    name: s.name,
    current: s.current,
    status: s.status,
    weekends: weekends.filter((w) => w.season_id === s.id || (w.season_id === null && s.id === current?.id)).map(row),
  }));
}

export async function channelManagers(weekendId: string): Promise<ChannelManager[]> {
  const rows = await q<PersonRow>(
    `SELECT u.id, u.name, u.email, u.role, u.photo_key, u.is_dev FROM channel_managers cm JOIN users u ON u.id = cm.user_id
     WHERE cm.weekend_id = $1 ORDER BY u.name NULLS LAST, u.email`,
    [weekendId],
  );
  return rows.map(personCard);
}

/** Admin: exactly these coordinators manage the weekend's channel (admins post everywhere already). */
export async function setChannelManagers(admin: SessionUser, weekendId: string, userIds: string[]): Promise<ChannelManager[]> {
  if (admin.role !== "admin") throw new AuthError(403, "Only an admin picks channel managers.");
  const ids = Array.from(new Set(userIds));
  await tx(async (c) => {
    await c.query("DELETE FROM channel_managers WHERE weekend_id = $1", [weekendId]);
    if (ids.length > 0) {
      await c.query(
        `INSERT INTO channel_managers (weekend_id, user_id)
         SELECT $1, u.id FROM users u WHERE u.id = ANY($2::uuid[]) AND u.status = 'active' AND u.role = 'coordinator'`,
        [weekendId, ids],
      );
    }
  });
  return channelManagers(weekendId);
}

/** Who may be picked as a channel manager: active coordinators. */
export async function managerCandidates(): Promise<ChannelManager[]> {
  const rows = await q<PersonRow>(
    `SELECT u.id, u.name, u.email, u.role, u.photo_key, u.is_dev FROM users u WHERE u.status = 'active' AND u.role = 'coordinator'
     ORDER BY u.name NULLS LAST, u.email`,
  );
  return rows.map(personCard);
}

export async function categoryManagers(categoryId: string): Promise<ChannelManager[]> {
  const rows = await q<PersonRow>(
    `SELECT u.id, u.name, u.email, u.role, u.photo_key, u.is_dev FROM category_managers cm JOIN users u ON u.id = cm.user_id
     WHERE cm.category_id = $1 ORDER BY u.name NULLS LAST, u.email`,
    [categoryId],
  );
  return rows.map(personCard);
}

/** Admin: exactly these coordinators manage the category's channel. */
export async function setCategoryManagers(admin: SessionUser, categoryId: string, userIds: string[]): Promise<ChannelManager[]> {
  if (admin.role !== "admin") throw new AuthError(403, "Only an admin picks channel managers.");
  const ids = Array.from(new Set(userIds));
  await tx(async (c) => {
    await c.query("DELETE FROM category_managers WHERE category_id = $1", [categoryId]);
    if (ids.length > 0) {
      await c.query(
        `INSERT INTO category_managers (category_id, user_id)
         SELECT $1, u.id FROM users u WHERE u.id = ANY($2::uuid[]) AND u.status = 'active' AND u.role = 'coordinator'`,
        [categoryId, ids],
      );
    }
  });
  return categoryManagers(categoryId);
}

/** Mute or unmute a channel's notifications for this person (push only; unread still counts). */
export async function setMuted(userId: string, conversationId: string, muted: boolean): Promise<boolean> {
  if (muted) await run("INSERT INTO channel_mutes (user_id, conversation_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [userId, conversationId]);
  else await run("DELETE FROM channel_mutes WHERE user_id = $1 AND conversation_id = $2", [userId, conversationId]);
  return muted;
}

export async function isMuted(userId: string, conversationId: string): Promise<boolean> {
  return (await q("SELECT 1 FROM channel_mutes WHERE user_id = $1 AND conversation_id = $2", [userId, conversationId])).length > 0;
}

export async function isChannelManager(userId: string, weekendId: string): Promise<boolean> {
  return (await run("SELECT 1 FROM channel_managers WHERE weekend_id = $1 AND user_id = $2", [weekendId, userId])) > 0;
}
