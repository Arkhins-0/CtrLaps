import "server-only";

import { one, q, run, tx } from "./db";
import type { SessionUser } from "./auth";
import { currentSeason } from "./seasons";
import { categoriesOf, type Category } from "./categories";

/*
 * Teams as records, the race categories each is entered in (per season, through the categories), and the
 * categories a person races in or looks after. See wink-docs/13-race-categories-plan.md, step 2.
 */

export type Team = { id: string; name: string; categoryIds: string[]; members: number };

/** Every team, with its entries in this season and how many people carry it. */
export async function listTeams(seasonId: string): Promise<Team[]> {
  const rows = await q<{ id: string; name: string; category_ids: string[] | null; members: number }>(
    `SELECT t.id, t.name,
            (SELECT array_agg(te.category_id ORDER BY c.position) FROM team_entries te JOIN categories c ON c.id = te.category_id
              WHERE te.team_id = t.id AND c.season_id = $1) AS category_ids,
            (SELECT count(*)::int FROM users u WHERE u.team_id = t.id) AS members
     FROM teams t ORDER BY lower(t.name)`,
    [seasonId],
  );
  return rows.map((r) => ({ id: r.id, name: r.name, categoryIds: r.category_ids ?? [], members: r.members }));
}

/** Set a team's entries for a season: only that season's categories are touched. */
export async function setTeamEntries(teamId: string, seasonId: string, categoryIds: string[]): Promise<void> {
  await tx(async (c) => {
    await c.query(
      "DELETE FROM team_entries te USING categories c WHERE te.team_id = $1 AND c.id = te.category_id AND c.season_id = $2",
      [teamId, seasonId],
    );
    if (categoryIds.length > 0) {
      await c.query(
        "INSERT INTO team_entries (team_id, category_id) SELECT $1, c.id FROM categories c WHERE c.season_id = $2 AND c.id = ANY($3::uuid[]) ON CONFLICT DO NOTHING",
        [teamId, seasonId, categoryIds],
      );
    }
  });
}

export async function teamByName(name: string): Promise<{ id: string; name: string } | null> {
  return (await one<{ id: string; name: string }>("SELECT id, name FROM teams WHERE lower(name) = lower($1)", [name.trim()])) ?? null;
}

export async function createTeam(name: string): Promise<string> {
  const row = await one<{ id: string }>("INSERT INTO teams (name) VALUES ($1) RETURNING id", [name.trim()]);
  return row!.id;
}

/** Rename: everyone carrying the team carries the new name too. */
export async function renameTeam(id: string, name: string): Promise<void> {
  await run("UPDATE teams SET name = $2 WHERE id = $1", [id, name.trim()]);
  await run("UPDATE users SET team_name = $2 WHERE team_id = $1", [id, name.trim()]);
}

/** Delete: its people keep no team. */
export async function deleteTeam(id: string): Promise<void> {
  await run("UPDATE users SET team_name = NULL WHERE team_id = $1", [id]);
  await run("DELETE FROM teams WHERE id = $1", [id]);
}

/** Point these people's team_id at the team their team_name names, making the team when it is new. */
export async function syncTeamIds(userIds: string[]): Promise<void> {
  if (userIds.length === 0) return;
  await run(
    `INSERT INTO teams (name)
     SELECT DISTINCT ON (lower(trim(team_name))) trim(team_name) FROM users
     WHERE id = ANY($1::uuid[]) AND coalesce(trim(team_name), '') <> ''
     ON CONFLICT DO NOTHING`,
    [userIds],
  );
  await run(
    `UPDATE users u SET team_id = (SELECT t.id FROM teams t WHERE lower(t.name) = lower(trim(u.team_name)))
     WHERE u.id = ANY($1::uuid[])`,
    [userIds],
  );
}

/** The categories given to one person (a racer's classes, an official's), in this season. */
export async function assignedCategories(userId: string, seasonId: string): Promise<string[]> {
  const rows = await q<{ category_id: string }>(
    "SELECT cp.category_id FROM category_people cp JOIN categories c ON c.id = cp.category_id WHERE cp.user_id = $1 AND c.season_id = $2 ORDER BY c.position",
    [userId, seasonId],
  );
  return rows.map((r) => r.category_id);
}

export async function setAssignedCategories(userId: string, seasonId: string, categoryIds: string[]): Promise<void> {
  await tx(async (c) => {
    await c.query("DELETE FROM category_people cp USING categories c WHERE cp.user_id = $1 AND c.id = cp.category_id AND c.season_id = $2", [userId, seasonId]);
    if (categoryIds.length > 0) {
      await c.query(
        "INSERT INTO category_people (user_id, category_id) SELECT $1, c.id FROM categories c WHERE c.season_id = $2 AND c.id = ANY($3::uuid[]) ON CONFLICT DO NOTHING",
        [userId, seasonId, categoryIds],
      );
    }
  });
}

/** The categories a team is entered in, this season. */
export async function teamCategories(teamId: string | null, seasonId: string): Promise<string[]> {
  if (!teamId) return [];
  const rows = await q<{ category_id: string }>(
    "SELECT te.category_id FROM team_entries te JOIN categories c ON c.id = te.category_id WHERE te.team_id = $1 AND c.season_id = $2 ORDER BY c.position",
    [teamId, seasonId],
  );
  return rows.map((r) => r.category_id);
}

/**
 * "My categories" in the current season, or null for everything: a racer's own classes (else their team's); crew and
 * team managers their team's; a race official the ones given to them. Everyone else — and anyone with none — sees all.
 */
export async function myCategories(user: Pick<SessionUser, "id" | "role"> & { team_id?: string | null }): Promise<string[] | null> {
  const season = (await currentSeason()).id;
  const team = user.team_id ?? (await one<{ team_id: string | null }>("SELECT team_id FROM users WHERE id = $1", [user.id]))?.team_id ?? null;
  let ids: string[] = [];
  switch (user.role) {
    case "racer":
      ids = await assignedCategories(user.id, season);
      if (ids.length === 0) ids = await teamCategories(team, season);
      break;
    case "crew":
    case "team_manager":
      ids = await teamCategories(team, season);
      break;
    case "user":
      ids = await followedCategories(user.id, season);
      break;
  }
  return ids.length > 0 ? ids : null;
}

/** The categories a user (no role yet) follows as a fan this season. */
export async function followedCategories(userId: string, seasonId: string): Promise<string[]> {
  const rows = await q<{ category_id: string }>(
    `SELECT f.category_id FROM category_followers f JOIN categories c ON c.id = f.category_id
     WHERE f.user_id = $1 AND c.season_id = $2 ORDER BY c.position`,
    [userId, seasonId],
  );
  return rows.map((r) => r.category_id);
}

/** Replace what a user follows this season with [categoryIds] (unknown or other seasons' ids are ignored). */
export async function setFollowedCategories(userId: string, seasonId: string, categoryIds: string[]): Promise<string[]> {
  await tx(async (c) => {
    await c.query(
      "DELETE FROM category_followers f USING categories c WHERE c.id = f.category_id AND f.user_id = $1 AND c.season_id = $2",
      [userId, seasonId],
    );
    await c.query(
      `INSERT INTO category_followers (user_id, category_id)
       SELECT $1, id FROM categories WHERE season_id = $2 AND id = ANY($3::uuid[]) ON CONFLICT DO NOTHING`,
      [userId, seasonId, categoryIds],
    );
  });
  return followedCategories(userId, seasonId);
}

/**
 * The categories to show as badges on someone's ID card: a racer's, crew's and team manager's (see myCategories) and
 * a race official's assigned ones. Nothing for roles that cover every class, or for a fan's follows (the card says
 * where someone belongs, not what they like).
 */
export async function categoryBadges(user: Pick<SessionUser, "id" | "role"> & { team_id?: string | null }): Promise<Category[]> {
  if (user.role === "user") return [];
  const ids = await myCategories(user);
  if (!ids) return [];
  const all = await categoriesOf([(await currentSeason()).id]);
  return all.filter((c) => ids.includes(c.id));
}
