import "server-only";

import { AuthError, type SessionUser } from "./auth";
import { categoryMemberSql, isCategoryMember } from "./categoryChannels";
import { categoriesOf } from "./categories";
import { one, q, run, tx } from "./db";
import { currentSeason, listSeasons } from "./seasons";
import { myCategories } from "./teams";
import { rowPoints, scoresByName, type Scoring } from "./scoring";
import { resultsAudience } from "./emailPrefs";
import { sendResults } from "./email";
import { pushTo } from "./push";
import { formatIn } from "./time";
import { SITE_URL } from "./config";

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
  /** When its results were first sent to the people who follow them (null: never). */
  notifiedAt: string | null;
};

export async function resultSession(sessionId: string): Promise<ResultSession | null> {
  const row = await one<{ id: string; name: string; starts_at: string; weekend_id: string; weekend_name: string; category_id: string | null; scores: boolean | null; results_notified_at: string | null }>(
    `SELECT s.id, s.name, s.starts_at, s.weekend_id, w.name AS weekend_name, s.category_id, s.scores, s.results_notified_at
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
        notifiedAt: row.results_notified_at ? new Date(row.results_notified_at).toISOString() : null,
      }
    : null;
}

/**
 * A session's results to the people who follow its category (their own categories, or ones they chose): a push to
 * them all, and the results email to those who kept it on. Recorded, so the editor knows they went.
 */
export async function notifyResults(sessionId: string): Promise<void> {
  const info = await one<{ name: string; starts_at: string; weekend_name: string; venue: string; timezone: string; category_id: string | null; code: string; category_name: string }>(
    `SELECT s.name, s.starts_at, w.name AS weekend_name, w.venue, w.timezone, s.category_id, c.code, c.name AS category_name
     FROM race_sessions s JOIN race_weekends w ON w.id = s.weekend_id JOIN categories c ON c.id = s.category_id WHERE s.id = $1`,
    [sessionId],
  );
  if (!info?.category_id) return;
  const rows = await sessionResults(sessionId);
  if (rows.length === 0) return;
  await run("UPDATE race_sessions SET results_notified_at = now() WHERE id = $1", [sessionId]);
  const podium = rows.filter((r) => r.status === "finished" && r.position !== null).slice(0, 3).map((r) => `${r.position}. ${r.driverName}`).join(" · ");
  const audience = await resultsAudience(info.category_id);
  const link = `/results/${sessionId}`;
  await pushTo(audience.push, { title: `${info.code} · ${info.name} results`, body: podium || "The results are in.", link, tag: `r-${sessionId}` }).catch(
    (error) => console.error("[results] push", error),
  );
  if (audience.email.length === 0) return;
  const people = await q<{ email: string; name: string | null; mail_token: string }>("SELECT email, name, mail_token FROM users WHERE id = ANY($1::uuid[])", [audience.email]);
  const table = await standings(info.category_id);
  const leaders = table.drivers.filter((d) => d.points > 0).slice(0, 3).map((d) => ({ name: d.name, points: d.points }));
  await sendResults(
    people.map((p) => ({ email: p.email, name: p.name, mailToken: p.mail_token })),
    {
      title: `${info.category_name} · ${info.name} results`,
      where: [info.weekend_name, info.venue, formatIn(info.starts_at, info.timezone)].filter((x) => x && x !== "TBA").join(" · "),
      rows: rows.map((r) => ({ position: r.position, status: r.status, carNumber: r.carNumber, driverName: r.driverName, teamName: r.teamName, points: r.points, pole: r.pole, fastestLap: r.fastestLap })),
      championship: leaders.length ? { heading: `${info.code} championship after ${info.weekend_name}`, leaders } : null,
      showPoints: rows.some((r) => r.points > 0),
      link: `${SITE_URL}${link}`,
    },
  ).catch((error) => console.error("[results] email", error));
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
  /** The racer picked from the list (their account); null for a name typed in. */
  userId: string | null;
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
  const uuid = (v: unknown) => (typeof v === "string" && /^[0-9a-f-]{36}$/i.test(v) ? v : null);
  const teamId = uuid(r.teamId);
  return {
    position,
    status,
    carNumber: text(r.carNumber, 10),
    driverName,
    userId: uuid(r.userId),
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
 * come from the table unless they were typed by hand. A driver picked from the list is that racer; a typed name is
 * linked to a racer when it matches exactly one.
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
  const racers = await q<{ id: string; name: string | null }>(
    "SELECT id, lower(trim(name)) AS name FROM users WHERE role = 'racer' AND status = 'active'",
  );
  const racerIds = new Set(racers.map((r) => r.id));
  const byName = new Map<string, string | null>();
  // A name two racers share links to neither.
  for (const r of racers) if (r.name) byName.set(r.name, byName.has(r.name) ? null : r.id);
  const picked = rows.map((r) => r.userId).filter((id): id is string => Boolean(id));
  if (new Set(picked).size !== picked.length) throw new AuthError(400, "A driver is in the list twice.");
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
          r.userId && racerIds.has(r.userId) ? r.userId : (byName.get(r.driverName.toLowerCase()) ?? null),
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

/** A session with results, as a column of the standings grid (oldest first). */
export type StandingSession = { id: string; name: string; startsAt: string; weekendName: string; rows: number };

/** One driver's result in one session, for the grid. */
export type DriverRound = { position: number | null; status: ResultStatus; points: number; pole: boolean; fastestLap: boolean };

export type DriverStanding = {
  /** The account id, or the typed name in lower case. */
  key: string;
  name: string;
  userId: string | null;
  carNumber: string;
  teamName: string | null;
  points: number;
  wins: number;
  podiums: number;
  starts: number;
  best: number | null;
  /** Their result per session id (sessions they weren't in are left out). */
  rounds: Record<string, DriverRound>;
};
export type TeamStanding = { id: string; name: string; points: number; wins: number; podiums: number; rounds: Record<string, number> };

type StandingRow = {
  session_id: string;
  starts_at: string;
  position: number | null;
  status: ResultStatus;
  car_number: string;
  driver_name: string;
  user_id: string | null;
  user_name: string | null;
  team_id: string | null;
  team_name: string | null;
  points: string;
  pole: boolean;
  fastest_lap: boolean;
};

const byPoints = <T extends { points: number; wins: number; podiums: number; name: string }>(a: T, b: T) =>
  b.points - a.points || b.wins - a.wins || b.podiums - a.podiums || a.name.localeCompare(b.name);

/**
 * A category's standings: drivers (an account's results together, else by the typed name) and teams, each with
 * points per session for the round-by-round grid; and the sessions with results, oldest first. Order: points, then
 * wins, then podiums (then the best finish for drivers), then the name.
 */
export async function standings(categoryId: string): Promise<{ drivers: DriverStanding[]; teams: TeamStanding[]; sessions: StandingSession[] }> {
  const [rows, sessions] = await Promise.all([
    q<StandingRow>(
      `SELECT r.session_id, s.starts_at, r.position, r.status, r.car_number, r.driver_name, r.user_id,
              NULLIF(u.name, '') AS user_name, r.team_id, t.name AS team_name, r.points::text AS points, r.pole, r.fastest_lap
       FROM session_results r JOIN race_sessions s ON s.id = r.session_id
       LEFT JOIN users u ON u.id = r.user_id LEFT JOIN teams t ON t.id = r.team_id
       WHERE s.category_id = $1 ORDER BY s.starts_at, r.row_order`,
      [categoryId],
    ),
    q<{ id: string; name: string; starts_at: string; weekend_name: string; rows: string }>(
      `SELECT s.id, s.name, s.starts_at, w.name AS weekend_name, count(r.id)::text AS rows
       FROM race_sessions s JOIN race_weekends w ON w.id = s.weekend_id JOIN session_results r ON r.session_id = s.id
       WHERE s.category_id = $1 GROUP BY s.id, w.name ORDER BY s.starts_at`,
      [categoryId],
    ),
  ]);
  const drivers = new Map<string, DriverStanding>();
  const teams = new Map<string, TeamStanding>();
  // A name typed before the racer had an account (a late entry) counts with that account: one active racer of that
  // exact name (letter case aside), or a racer already in these results under it. A name two racers share stays typed.
  const typedNames = Array.from(new Set(rows.filter((r) => !r.user_id).map((r) => r.driver_name.trim().toLowerCase())));
  const named = typedNames.length
    ? await q<{ id: string; name: string; user_name: string }>(
        `SELECT id, lower(trim(name)) AS name, name AS user_name FROM users
         WHERE role = 'racer' AND status = 'active' AND lower(trim(name)) = ANY($1::text[])`,
        [typedNames],
      )
    : [];
  const accountByName = new Map<string, string>();
  const nameOf = new Map<string, string>();
  for (const n of named) {
    if (named.filter((x) => x.name === n.name).length === 1) accountByName.set(n.name, n.id);
    nameOf.set(n.id, n.user_name);
  }
  for (const r of rows) if (r.user_id && r.user_name) accountByName.set(r.user_name.trim().toLowerCase(), r.user_id);
  // Rows come oldest first, so the latest name, car and team win.
  for (const r of rows) {
    const typed = r.driver_name.trim().toLowerCase();
    const key = r.user_id ?? accountByName.get(typed) ?? typed;
    const points = Number(r.points);
    const won = r.position === 1;
    const podium = r.position !== null && r.position <= 3;
    const d = drivers.get(key) ?? { key, name: "", userId: null, carNumber: "", teamName: null, points: 0, wins: 0, podiums: 0, starts: 0, best: null, rounds: {} };
    // The account: picked on the row, or found by the typed name; it carries the account's own name.
    if (r.user_id || nameOf.has(key)) d.userId = r.user_id ?? key;
    d.name = r.user_name ?? nameOf.get(key) ?? (d.userId ? d.name || r.driver_name : r.driver_name);
    if (r.car_number) d.carNumber = r.car_number;
    if (r.team_name) d.teamName = r.team_name;
    d.points += points;
    d.wins += won ? 1 : 0;
    d.podiums += podium ? 1 : 0;
    d.starts += r.status === "dns" ? 0 : 1;
    if (r.position !== null) d.best = d.best === null ? r.position : Math.min(d.best, r.position);
    d.rounds[r.session_id] = { position: r.position, status: r.status, points, pole: r.pole, fastestLap: r.fastest_lap };
    drivers.set(key, d);
    if (r.team_id && r.team_name) {
      const t = teams.get(r.team_id) ?? { id: r.team_id, name: r.team_name, points: 0, wins: 0, podiums: 0, rounds: {} };
      t.points += points;
      t.wins += won ? 1 : 0;
      t.podiums += podium ? 1 : 0;
      t.rounds[r.session_id] = (t.rounds[r.session_id] ?? 0) + points;
      teams.set(r.team_id, t);
    }
  }
  const round2 = (n: number) => Math.round(n * 100) / 100;
  return {
    drivers: Array.from(drivers.values())
      .map((d) => ({ ...d, points: round2(d.points) }))
      .sort((a, b) => byPoints(a, b) || (a.best ?? 999) - (b.best ?? 999)),
    teams: Array.from(teams.values())
      .map((t) => ({ ...t, points: round2(t.points) }))
      .sort(byPoints),
    sessions: sessions.map((x) => ({ id: x.id, name: x.name, startsAt: new Date(x.starts_at).toISOString(), weekendName: x.weekend_name, rows: Number(x.rows) })),
  };
}

/** A racer who may be picked for a result: their account, name, and the car number of their last result here. */
export type Entrant = { id: string; name: string; carNumber: string };
export type EntrantTeam = { id: string; name: string; entered: boolean; racers: Entrant[] };

/**
 * Who can be picked when entering a category's results: the teams entered in it (and any team one of its racers is
 * in), each with its racers in this category; racers without a team under a null team id; and every other team, to
 * pick for a driver typed in. Racers in a category: their own classes, else their team's entries.
 */
export async function entrants(categoryId: string): Promise<EntrantTeam[]> {
  const [racers, teamRows] = await Promise.all([
    q<{ id: string; name: string; team_id: string | null; car: string | null }>(
      `SELECT u.id, COALESCE(NULLIF(u.name, ''), u.email) AS name, u.team_id,
              (SELECT r.car_number FROM session_results r JOIN race_sessions s ON s.id = r.session_id
                WHERE r.user_id = u.id AND s.category_id = $1 AND r.car_number <> '' ORDER BY s.starts_at DESC LIMIT 1) AS car
       FROM users u WHERE u.role = 'racer' AND ${categoryMemberSql("u", "$1")}
       ORDER BY lower(COALESCE(NULLIF(u.name, ''), u.email))`,
      [categoryId],
    ),
    q<{ id: string; name: string; entered: boolean }>(
      `SELECT t.id, t.name, EXISTS (SELECT 1 FROM team_entries te WHERE te.team_id = t.id AND te.category_id = $1) AS entered
       FROM teams t ORDER BY lower(t.name)`,
      [categoryId],
    ),
  ]);
  const withRacers = new Set(racers.map((r) => r.team_id));
  const teams: EntrantTeam[] = teamRows.map((t) => ({
    id: t.id,
    name: t.name,
    entered: t.entered || withRacers.has(t.id),
    racers: racers.filter((r) => r.team_id === t.id).map((r) => ({ id: r.id, name: r.name, carNumber: r.car ?? "" })),
  }));
  const loose = racers.filter((r) => !r.team_id || !teamRows.some((t) => t.id === r.team_id));
  // Entered teams first, then the rest; racers with no team last.
  return [
    ...teams.filter((t) => t.entered),
    ...teams.filter((t) => !t.entered),
    ...(loose.length ? [{ id: "", name: "No team", entered: true, racers: loose.map((r) => ({ id: r.id, name: r.name, carNumber: r.car ?? "" })) }] : []),
  ];
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
