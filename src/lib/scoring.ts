// Shared by server and client: no "server-only", no Node built-ins.

/**
 * A category's points table: `points[0]` for the winner, `points[1]` for second…; what a DNF, a DNS and a DSQ score;
 * and the bonus for starting from pole and for the fastest lap.
 */
export type Scoring = { points: number[]; dnf: number; dns: number; dsq: number; pole: number; fastestLap: number };

/** A common table to start from: 25, 18, 15, 12, 10, 8, 6, 4, 2, 1; nothing for a DNF, DNS or DSQ; no bonuses. */
export const DEFAULT_SCORING: Scoring = { points: [25, 18, 15, 12, 10, 8, 6, 4, 2, 1], dnf: 0, dns: 0, dsq: 0, pole: 0, fastestLap: 0 };

const num = (v: unknown): number | null => {
  const n = typeof v === "string" ? Number(v.trim()) : typeof v === "number" ? v : NaN;
  return Number.isFinite(n) && n >= 0 && n < 1000 ? Math.round(n * 100) / 100 : null;
};

/** A table from a request, checked; null (no table) for null; an error for anything else wrong. */
export function scoringInput(raw: unknown): Scoring | null | { error: string } {
  if (raw === null) return null;
  const r = (raw ?? {}) as Record<string, unknown>;
  if (!Array.isArray(r.points)) return { error: "List the points for each position." };
  if (r.points.length > 60) return { error: "Up to 60 positions." };
  const points: number[] = [];
  for (const p of r.points) {
    const n = num(p);
    if (n === null) return { error: "Points must be numbers from 0 to 999." };
    points.push(n);
  }
  const field = (k: string) => (r[k] === undefined || r[k] === "" ? 0 : num(r[k]));
  const out = { points, dnf: field("dnf"), dns: field("dns"), dsq: field("dsq"), pole: field("pole"), fastestLap: field("fastestLap") };
  if (Object.values(out).some((v) => v === null)) return { error: "Points must be numbers from 0 to 999." };
  return out as Scoring;
}

/** "25, 18, 15" ⇄ [25, 18, 15], for the editors. */
export const pointsText = (points: number[]): string => points.join(", ");
export const pointsList = (text: string): number[] => text.split(/[\s,]+/).filter(Boolean).map(Number);

/** What one result row scores from the table: its position (or DNF / DNS / DSQ), plus pole and fastest lap. */
export function rowPoints(s: Scoring, r: { status: string; position: number | null; pole?: boolean; fastestLap?: boolean }): number {
  const base =
    r.status === "finished" ? (r.position ? s.points[r.position - 1] ?? 0 : 0) : r.status === "dnf" ? s.dnf : r.status === "dns" ? s.dns : r.status === "dsq" ? s.dsq : 0;
  return Math.round((base + (r.pole ? s.pole : 0) + (r.fastestLap ? s.fastestLap : 0)) * 100) / 100;
}

/** Whether a session scores from the table when nobody said: not qualifying, practice, warm-up, test or shakedown. */
export const scoresByName = (name: string): boolean => !/\b(qualif\w*|practi[cs]e\w*|warm[- ]?up|test|shakedown|fp\d?|q\d?)\b/i.test(name);

/** One line about a table: "25, 18, 15… · DNF 0 · pole +1 · fastest lap +1". */
export function scoringSummary(s: Scoring): string {
  const head = s.points.length > 6 ? `${pointsText(s.points.slice(0, 6))}…` : pointsText(s.points);
  const extras = [
    s.dnf ? `DNF ${s.dnf}` : "",
    s.dns ? `DNS ${s.dns}` : "",
    s.dsq ? `DSQ ${s.dsq}` : "",
    s.pole ? `pole +${s.pole}` : "",
    s.fastestLap ? `fastest lap +${s.fastestLap}` : "",
  ].filter(Boolean);
  return [head || "No points", ...extras].join(" · ");
}
