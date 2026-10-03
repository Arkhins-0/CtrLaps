import "server-only";

import type { SessionUser } from "./auth";
import { one, q } from "./db";
import { currentSeason, LIVE_SEASON } from "./seasons";

/*
 * One channel per race category, for the category's season ("ITC 2026"). Who is in it follows from the entries
 * (see teams.ts): admins and coordinators; the category's racers (their own classes, else their team's); crew and team
 * managers of a team entered in it; race officials given it, or given no category that season (they look after all);
 * users (no role yet) who follow it as fans. That membership still decides results notices and "My categories"; the
 * channel itself is read by everyone and posts reach everyone (who may mute it). Admins and its managers post.
 */

/** The SQL test "user row u is a member of category $cat", for reuse in queries. */
export const categoryMemberSql = (u: string, cat: string) => `(
  ${u}.status = 'active' AND (
    ${u}.role IN ('admin', 'coordinator')
    OR (${u}.role = 'racer' AND (
      EXISTS (SELECT 1 FROM category_people cp WHERE cp.user_id = ${u}.id AND cp.category_id = ${cat})
      OR (NOT EXISTS (SELECT 1 FROM category_people cp JOIN categories c2 ON c2.id = cp.category_id
                      WHERE cp.user_id = ${u}.id AND c2.season_id = (SELECT season_id FROM categories WHERE id = ${cat}))
          AND EXISTS (SELECT 1 FROM team_entries te WHERE te.team_id = ${u}.team_id AND te.category_id = ${cat}))))
    OR (${u}.role IN ('crew', 'team_manager') AND EXISTS (SELECT 1 FROM team_entries te WHERE te.team_id = ${u}.team_id AND te.category_id = ${cat}))
    OR (${u}.role = 'race_official' AND (
      EXISTS (SELECT 1 FROM category_people cp WHERE cp.user_id = ${u}.id AND cp.category_id = ${cat})
      OR NOT EXISTS (SELECT 1 FROM category_people cp JOIN categories c2 ON c2.id = cp.category_id
                     WHERE cp.user_id = ${u}.id AND c2.season_id = (SELECT season_id FROM categories WHERE id = ${cat}))))
    OR (${u}.role = 'user' AND EXISTS (SELECT 1 FROM category_followers cf WHERE cf.user_id = ${u}.id AND cf.category_id = ${cat}))
  ))`;

/** Everyone in a category's channel, but [except]. */
export async function categoryMemberIds(categoryId: string, except?: string): Promise<string[]> {
  const rows = await q<{ id: string }>(`SELECT u.id FROM users u WHERE ${categoryMemberSql("u", "$1")} AND u.id <> $2`, [categoryId, except ?? "00000000-0000-0000-0000-000000000000"]);
  return rows.map((r) => r.id);
}

export async function isCategoryMember(userId: string, categoryId: string): Promise<boolean> {
  return Boolean(await one(`SELECT 1 FROM users u WHERE u.id = $2 AND ${categoryMemberSql("u", "$1")}`, [categoryId, userId]));
}

/** Admins post in every category channel; coordinators in those an admin made them a manager of. */
export async function canPostCategory(user: SessionUser, categoryId: string): Promise<boolean> {
  if (user.role === "admin") return true;
  return Boolean(await one("SELECT 1 FROM category_managers WHERE category_id = $1 AND user_id = $2", [categoryId, user.id]));
}

export type CategoryInfo = { id: string; seasonId: string; name: string; code: string; color: string; open: boolean; seasonName: string };

/** The category, and whether its channel is open (it is while its season is live). */
export async function categoryInfo(categoryId: string): Promise<CategoryInfo | null> {
  const row = await one<{ id: string; season_id: string; name: string; code: string; color: string; status: string; season_name: string }>(
    `SELECT c.id, c.season_id, c.name, c.code, c.color, s.status, s.name AS season_name FROM categories c JOIN seasons s ON s.id = c.season_id WHERE c.id = $1`,
    [categoryId],
  );
  return row
    ? { id: row.id, seasonId: row.season_id, name: row.name, code: row.code, color: row.color, open: row.status === "active", seasonName: row.season_name }
    : null;
}

/** The category's conversation, made the first time it is needed. */
export async function categoryConversation(categoryId: string): Promise<string> {
  const find = () => one<{ id: string }>("SELECT id FROM conversations WHERE kind = 'category' AND category_id = $1", [categoryId]);
  const existing = await find();
  if (existing) return existing.id;
  const made = await one<{ id: string }>(
    "INSERT INTO conversations (kind, category_id) VALUES ('category', $1) ON CONFLICT DO NOTHING RETURNING id",
    [categoryId],
  );
  return made?.id ?? (await find())!.id;
}

export type CategoryChannel = {
  id: string;
  name: string;
  code: string;
  color: string;
  unread: number;
  lastMessageAt: string | null;
  lastMessage: string | null;
  /** This person muted its notifications. */
  muted: boolean;
};

/** The current season's category channels (everyone sees them all), with unread counts, the last post and mute. */
export async function listCategoryChannels(user: SessionUser): Promise<CategoryChannel[]> {
  const season = await currentSeason();
  const rows = await q<{
    id: string;
    name: string;
    code: string;
    color: string;
    unread: string;
    last_at: string | null;
    last_body: string | null;
    muted: boolean;
  }>(
    `SELECT c.id, c.name, c.code, c.color,
            EXISTS (SELECT 1 FROM channel_mutes mu WHERE mu.user_id = $1 AND mu.conversation_id = cv.id) AS muted,
            COALESCE((SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
                      WHERE m.conversation_id = cv.id AND r.read_at IS NULL AND ${LIVE_SEASON("m")}), 0)::text AS unread,
            (SELECT m.created_at FROM messages m WHERE m.conversation_id = cv.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_at,
            (SELECT COALESCE(NULLIF(u.name, ''), u.email, 'Someone') || ': ' ||
                    CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted' WHEN m.body <> '' THEN m.body ELSE 'Document' END
               FROM messages m LEFT JOIN users u ON u.id = m.sender_id
               WHERE m.conversation_id = cv.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_body
     FROM categories c
     LEFT JOIN conversations cv ON cv.kind = 'category' AND cv.category_id = c.id
     WHERE c.season_id = $2
     ORDER BY c.position`,
    [user.id, season.id],
  );
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    code: r.code,
    color: r.color,
    unread: Number(r.unread),
    lastMessageAt: r.last_at ? new Date(r.last_at).toISOString() : null,
    lastMessage: r.last_body ? (r.last_body.length > 140 ? `${r.last_body.slice(0, 137)}…` : r.last_body) : null,
    muted: r.muted,
  }));
}

export type RosterCategory = { id: string; seasonId: string; name: string; code: string; color: string; position: number; memberIds: string[] };

/**
 * The current season's categories, each with the people tied to it by name: its racers (their own classes, else their
 * team's), the crew and team managers of teams entered in it, and the race officials given it. Admins, coordinators
 * and officials who look after every class are left out: they belong to all. For picking an audience or a filter.
 */
export async function categoryRoster(): Promise<RosterCategory[]> {
  const season = await currentSeason();
  const rows = await q<{ id: string; season_id: string; name: string; code: string; color: string; position: number; member_ids: string[] | null }>(
    `SELECT c.id, c.season_id, c.name, c.code, c.color, c.position,
            (SELECT array_agg(u.id) FROM users u
              WHERE u.status = 'active' AND (
                (u.role = 'racer' AND (
                  EXISTS (SELECT 1 FROM category_people cp WHERE cp.user_id = u.id AND cp.category_id = c.id)
                  OR (NOT EXISTS (SELECT 1 FROM category_people cp JOIN categories c2 ON c2.id = cp.category_id
                                  WHERE cp.user_id = u.id AND c2.season_id = c.season_id)
                      AND EXISTS (SELECT 1 FROM team_entries te WHERE te.team_id = u.team_id AND te.category_id = c.id))))
                OR (u.role IN ('crew', 'team_manager') AND EXISTS (SELECT 1 FROM team_entries te WHERE te.team_id = u.team_id AND te.category_id = c.id))
                OR (u.role = 'race_official' AND EXISTS (SELECT 1 FROM category_people cp WHERE cp.user_id = u.id AND cp.category_id = c.id))
              )) AS member_ids
     FROM categories c WHERE c.season_id = $1 ORDER BY c.position`,
    [season.id],
  );
  return rows.map((r) => ({ id: r.id, seasonId: r.season_id, name: r.name, code: r.code, color: r.color, position: r.position, memberIds: r.member_ids ?? [] }));
}
