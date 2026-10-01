import "server-only";

import { AuthError, type SessionUser } from "./auth";
import { isCategoryMember } from "./categoryChannels";
import { categoriesOf } from "./categories";
import { one, q, tx } from "./db";
import { currentSeason, listSeasons } from "./seasons";
import { myCategories } from "./teams";
import { rowPoints, scoresByName, type Scoring } from "./scoring";

/*
 * Results and standings per race category. Results belong to a session that has a category; admins, coordinators and
 * the category's race officials enter them on the website. A driver is a name (with a car number and a team), linked
 * to a racer's account when the name matches exactly one racer. Points come from the category's points table (see
 * scoring.ts) when it has one and the session scores, unless typed by hand; standings add them up for the category (a
 * category belongs to one season): drivers, then teams.
 */

export const RESULT_STATUS = ["finished", "dnf", "dns", "dsq"] as const;
export type ResultStatus = (typeof RESULT_STATUS)[number];

export type ResultRow = {
  position: number | null;
  status: ResultStatus;
  carNumber: string;
  driverName: string;
  userId: string | null;
  teamId: string | null;
  teamName: string | null;
  points: number;
  bestLap: string;
  pole: boolean;
  fastestLap: boolean;
  /** Typed by hand: kept whatever the table says. */
  manualPoints: boolean;
};

export type ResultSession = {
  id: string;
  name: string;
  startsAt: string;
  weekendId: string;
  weekendName: string;
  categoryId: string | null;
  /** Whether it scores from the points table (asked, else by its name: qualifying and practice don't). */
  scores: boolean;
};

export async function resultSession(sessionId: string): Promise<ResultSession | null> {
  const row = await one<{ id: string; name: string; starts_at: string; weekend_id: string; weekend_name: string; category_id: string | null; scores: boolean | null }>(
    `SELECT s.id, s.name, s.starts_at, s.weekend_id, w.name AS weekend_name, s.category_id, s.scores
     FROM race_sessions s JOIN race_weekends w ON w.id = s.weekend_id WHERE s.id = $1`,
    [sessionId],
  );
  return row
    ? {
        id: row.id,
        name: row.name,
        startsAt: new Date(row.starts_at).toISOString(),
        weekendId: row.weekend_id,
        weekendName: row.weekend_name,
        categoryId: row.category_id,
        scores: row.scores ?? scoresByName(row.name),
      }
    : null;
}

/** A category's points table, if it has one. */
export async function categoryScoring(categoryId: string | null): Promise<Scoring | null> {
  if (!categoryId) return null;
  return (await one<{ scoring: Scoring | null }>("SELECT scoring FROM categories WHERE id = $1", [categoryId]))?.scoring ?? null;
}

/** Admins, coordinators and the category's race officials (or officials who look after every class). */
export async function canEnterResults(user: SessionUser, categoryId: string | null): Promise<boolean> {
  if (!categoryId) return false;
  if (user.role === "admin" || user.role === "coordinator") return true;
  return user.role === "race_official" && (await isCategoryMember(user.id, categoryId));
}

export async function sessionResults(sessionId: string): Promise<ResultRow[]> {
  const rows = await q<{
    position: number | null;
    status: ResultStatus;
    car_number: string;
    driver_name: string;
    user_id: string | null;
    team_id: string | null;
    team_name: string | null;
    points: string;
    best_lap: string;
    pole: boolean;
    fastest_lap: boolean;
    manual_points: boolean;
  }>(
    `SELECT r.position, r.status, r.car_number, r.driver_name, r.user_id, r.team_id, t.name AS team_name, r.points::text AS points, r.best_lap,
            r.pole, r.fastest_lap, r.manual_points
     FROM session_results r LEFT JOIN teams t ON t.id = r.team_id WHERE r.session_id = $1 ORDER BY r.row_order`,
    [sessionId],
  );
  return rows.map((r) => ({
    position: r.position,
    status: r.status,
    carNumber: r.car_number,
    driverName: r.driver_name,
    userId: r.user_id,
    teamId: r.team_id,
    teamName: r.team_name,
    points: Number(r.points),
    bestLap: r.best_lap,
    pole: r.pole,
    fastestLap: r.fastest_lap,
    manualPoints: r.manual_points,
  }));
}

export type ResultInput = {
  position: number | null;
  status: ResultStatus;
  carNumber: string;
  driverName: string;
  teamId: string | null;
  points: number;
  bestLap: string;
  pole: boolean;
  fastestLap: boolean;
  /** Points typed by hand; otherwise they come from the table (when the session scores from one). */
  manualPoints: boolean;
};

/** One row from a request body. A position only counts for a finisher; points are 0 or more. */
export function resultInput(raw: unknown): ResultInput {
  const r = (raw ?? {}) as Record<string, unknown>;
  const text = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");
  const driverName = text(r.driverName, 120);
  if (!driverName) throw new AuthError(400, "Every row needs a driver.");
  const status = RESULT_STATUS.includes(r.status as ResultStatus) ? (r.status as ResultStatus) : "finished";
  const pos = Number(r.position);
  const position = status === "finished" && Number.isInteger(pos) && pos > 0 && pos < 1000 ? pos : null;
  const pts = Number(r.points);
  const points = Number.isFinite(pts) && pts >= 0 && pts < 10000 ? Math.round(pts * 100) / 100 : 0;
  const teamId = typeof r.teamId === "string" && /^[0-9a-f-]{36}$/i.test(r.teamId) ? r.teamId : null;
  return {
    position,
    status,
    carNumber: text(r.carNumber, 10),
    driverName,
    teamId,
    points,
    bestLap: text(r.bestLap, 20),
    pole: r.pole === true,
    fastestLap: r.fastestLap === true,
    // Older apps send no flag: their points were typed.
    manualPoints: r.manualPoints !== false,
  };
}

/**
 * Replace a session's results, and whether it scores from the points table (`scores`, when given). A row's points
 * come from the table unless they were typed by hand. A driver whose name matches exactly one active racer is linked.
 */
export async function saveResults(session: ResultSession, rows: ResultInput[], scores?: boolean): Promise<void> {
  if (!session.categoryId) throw new AuthError(400, "Give this session a category first: results belong to a category.");
  if (rows.length > 200) throw new AuthError(400, "Up to 200 rows.");
  const positions = rows.map((r) => r.position).filter((p): p is number => p !== null);
  if (new Set(positions).size !== positions.length) throw new AuthError(400, "Two drivers have the same position.");
  if (rows.filter((r) => r.pole).length > 1) throw new AuthError(400, "Only one driver can have pole.");
  if (rows.filter((r) => r.fastestLap).length > 1) throw new AuthError(400, "Only one driver can have the fastest lap.");
  const scoring = await categoryScoring(session.categoryId);
  const scored = scores ?? session.scores;
  const pointsOf = (r: ResultInput) => (r.manualPoints || !scoring ? r.points : scored ? rowPoints(scoring, r) : 0);
  const racers = await q<{ id: string; name: string }>(
    `SELECT id, lower(trim(name)) AS name FROM users
     WHERE role = 'racer' AND status = 'active' AND name IS NOT NULL AND trim(name) <> ''`,
  );
  const byName = new Map<string, string | null>();
  // A name two racers share links to neither.
  for (const r of racers) byName.set(r.name, byName.has(r.name) ? null : r.id);
  const teamIds = rows.map((r) => r.teamId).filter((id): id is string => Boolean(id));
  const teams = new Set((await q<{ id: string }>("SELECT id FROM teams WHERE id = ANY($1::uuid[])", [teamIds])).map((t) => t.id));
  await tx(async (c) => {
    if (scores !== undefined) await c.query("UPDATE race_sessions SET scores = $2 WHERE id = $1", [session.id, scores]);
    await c.query("DELETE FROM session_results WHERE session_id = $1", [session.id]);
    for (const [i, r] of rows.entries()) {
      await c.query(
        `INSERT INTO session_results (session_id, row_order, position, status, car_number, driver_name, user_id, team_id, points, best_lap, pole, fastest_lap, manual_points)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)`,
        [
          session.id,
          i,
          r.position,
          r.status,
          r.carNumber,
          r.driverName,
          byName.get(r.driverName.toLowerCase()) ?? null,
          r.teamId && teams.has(r.teamId) ? r.teamId : null,
          pointsOf(r),
          r.bestLap,
          r.pole,
          r.fastestLap,
          r.manualPoints && Boolean(scoring),
        ],
      );
    }
  });
}

export type DriverStanding = {
  key: string;
  name: string;
  userId: string | null;
  carNumber: string;
  teamName: string | null;
  points: number;
  wins: number;
  starts: number;
  best: number | null;
};
export type TeamStanding = { id: string; name: string; points: number; wins: number };
export type StandingSession = { id: string; name: string; startsAt: string; weekendName: string; rows: number };

/** A category's standings, and the sessions that have results (newest first). */
export async function standings(categoryId: string): Promise<{ drivers: DriverStanding[]; teams: TeamStanding[]; sessions: StandingSession[] }> {
  const [drivers, teams, sessions] = await Promise.all([
    q<{ key: string; name: string; user_id: string | null; car_number: string | null; team_name: string | null; points: string; wins: string; starts: string; best: number | null }>(
      `WITH r AS (
         SELECT r.*, s.starts_at, COALESCE(r.user_id::text, lower(trim(r.driver_name))) AS key
         FROM session_results r JOIN race_sessions s ON s.id = r.session_id WHERE s.category_id = $1
       )
       SELECT r.key,
              (array_agg(COALESCE(NULLIF(u.name, ''), r.driver_name) ORDER BY r.starts_at DESC))[1] AS name,
              (array_agg(r.user_id) FILTER (WHERE r.user_id IS NOT NULL))[1] AS user_id,
              (array_agg(r.car_number ORDER BY r.starts_at DESC) FILTER (WHERE r.car_number <> ''))[1] AS car_number,
              (array_agg(t.name ORDER BY r.starts_at DESC) FILTER (WHERE t.name IS NOT NULL))[1] AS team_name,
              sum(r.points)::text AS points,
              count(*) FILTER (WHERE r.position = 1)::text AS wins,
              count(*) FILTER (WHERE r.status <> 'dns')::text AS starts,
              min(r.position) AS best
       FROM r LEFT JOIN users u ON u.id = r.user_id LEFT JOIN teams t ON t.id = r.team_id
       GROUP BY r.key`,
      [categoryId],
    ),
    q<{ id: string; name: string; points: string; wins: string }>(
      `SELECT t.id, t.name, sum(r.points)::text AS points, count(*) FILTER (WHERE r.position = 1)::text AS wins
       FROM session_results r JOIN race_sessions s ON s.id = r.session_id JOIN teams t ON t.id = r.team_id
       WHERE s.category_id = $1 GROUP BY t.id, t.name`,
      [categoryId],
    ),
    q<{ id: string; name: string; starts_at: string; weekend_name: string; rows: string }>(
      `SELECT s.id, s.name, s.starts_at, w.name AS weekend_name, count(r.id)::text AS rows
       FROM race_sessions s JOIN race_weekends w ON w.id = s.weekend_id JOIN session_results r ON r.session_id = s.id
       WHERE s.category_id = $1 GROUP BY s.id, w.name ORDER BY s.starts_at DESC`,
      [categoryId],
    ),
  ]);
  return {
    drivers: drivers
      .map((r) => ({
        key: r.key,
        name: r.name,
        userId: r.user_id,
        carNumber: r.car_number ?? "",
        teamName: r.team_name,
        points: Number(r.points),
        wins: Number(r.wins),
        starts: Number(r.starts),
        best: r.best,
      }))
      // Points, then wins, then the best finish, then the name.
      .sort((a, b) => b.points - a.points || b.wins - a.wins || (a.best ?? 999) - (b.best ?? 999) || a.name.localeCompare(b.name)),
    teams: teams
      .map((r) => ({ id: r.id, name: r.name, points: Number(r.points), wins: Number(r.wins) }))
      .sort((a, b) => b.points - a.points || b.wins - a.wins || a.name.localeCompare(b.name)),
    sessions: sessions.map((s) => ({ id: s.id, name: s.name, startsAt: new Date(s.starts_at).toISOString(), weekendName: s.weekend_name, rows: Number(s.rows) })),
  };
}

/**
 * A category's points table changed: every result row not typed by hand is scored again (zero in sessions that
 * don't score from the table).
 */
export async function setCategoryScoring(categoryId: string, scoring: Scoring | null): Promise<void> {
  await tx(async (c) => {
    await c.query("UPDATE categories SET scoring = $2 WHERE id = $1", [categoryId, scoring === null ? null : JSON.stringify(scoring)]);
    if (!scoring) return;
    const rows = (
      await c.query<{ id: string; status: string; position: number | null; pole: boolean; fastest_lap: boolean; name: string; scores: boolean | null }>(
        `SELECT r.id, r.status, r.position, r.pole, r.fastest_lap, s.name, s.scores
         FROM session_results r JOIN race_sessions s ON s.id = r.session_id
         WHERE s.category_id = $1 AND NOT r.manual_points`,
        [categoryId],
      )
    ).rows;
    for (const r of rows) {
      const points = (r.scores ?? scoresByName(r.name)) ? rowPoints(scoring, { status: r.status, position: r.position, pole: r.pole, fastestLap: r.fastest_lap }) : 0;
      await c.query("UPDATE session_results SET points = $2 WHERE id = $1", [r.id, points]);
    }
  });
}

/** The teams offered when entering a session's results: the ones entered in its category first, then the rest. */
export async function teamsForResults(categoryId: string): Promise<{ id: string; name: string; entered: boolean }[]> {
  const rows = await q<{ id: string; name: string; entered: boolean }>(
    `SELECT t.id, t.name, EXISTS (SELECT 1 FROM team_entries te WHERE te.team_id = t.id AND te.category_id = $1) AS entered
     FROM teams t ORDER BY 3 DESC, lower(t.name)`,
    [categoryId],
  );
  return rows;
}

/**
 * What the Standings page shows: the seasons to choose from (current first), the season asked for (else the current
 * one), its categories, the one chosen (the person's own first), and its standings. Archived seasons keep theirs.
 */
export async function standingsPage(user: SessionUser, asked: string | null, seasonAsked: string | null = null) {
  const current = await currentSeason();
  const all = await listSeasons();
  const season = all.find((s) => s.id === seasonAsked) ?? current;
  const seasons = [...all.filter((s) => s.id === current.id), ...all.filter((s) => s.id !== current.id)].map((s) => ({
    id: s.id,
    name: s.name,
    current: s.id === current.id,
  }));
  const categories = await categoriesOf([season.id]);
  const mine = season.id === current.id ? ((await myCategories(user)) ?? []) : [];
  const chosen = categories.find((c) => c.id === asked) ?? categories.find((c) => mine.includes(c.id)) ?? categories[0] ?? null;
  const empty = { drivers: [] as DriverStanding[], teams: [] as TeamStanding[], sessions: [] as StandingSession[] };
  return { seasons, seasonId: season.id, categories, categoryId: chosen?.id ?? null, ...(chosen ? await standings(chosen.id) : empty) };
}
