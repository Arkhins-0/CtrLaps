import Link from "next/link";
import { LocalTime } from "@/components/LocalTime";
import type { DriverRound, StandingSession } from "@/lib/results";
import { standingsPage } from "@/lib/results";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Standings" };

const STATUS: Record<string, string> = { dnf: "DNF", dns: "DNS", dsq: "DSQ" };

/** A position badge: gold for the podium. */
function Place({ n }: { n: number }) {
  return (
    <span className={`inline-flex h-7 min-w-7 items-center justify-center rounded-md px-1 text-xs font-bold ${n <= 3 ? "bg-gold text-night" : "bg-night-line"}`}>{n}</span>
  );
}

/** One driver's cell in the grid: their points, with the finish (or DNF…) small beneath; blank when they weren't in it. */
function RoundCell({ r }: { r?: DriverRound }) {
  if (!r) return <td className="px-2 py-2 text-center text-snow-faint">·</td>;
  return (
    <td className="px-2 py-2 text-center" title={r.status === "finished" ? `P${r.position ?? "–"}${r.pole ? " · pole" : ""}${r.fastestLap ? " · fastest lap" : ""}` : STATUS[r.status]}>
      <span className={`block font-medium ${r.position === 1 ? "text-gold" : ""}`}>{r.points || (r.status === "finished" ? "0" : "")}</span>
      <span className="block text-[10px] text-snow-faint">
        {r.status === "finished" ? `P${r.position ?? "–"}` : STATUS[r.status]}
        {r.pole ? " ·P" : ""}
        {r.fastestLap ? " ·FL" : ""}
      </span>
    </td>
  );
}

/** The grid's column heads: a short label per session (R1, R2…), the full name on hover. */
function RoundHead({ s, i }: { s: StandingSession; i: number }) {
  return (
    <th className="px-2 py-1.5 text-center font-medium" title={`${s.weekendName} · ${s.name}`}>
      <Link href={`/results/${s.id}`} className="hover:text-gold">
        R{i + 1}
      </Link>
    </th>
  );
}

/**
 * A season's standings (the current one, or an archived one), one race category at a time: drivers and teams in a
 * round-by-round grid (points per session, wins, podiums, total), and every session's results.
 */
export default async function Standings({ searchParams }: { searchParams: Promise<{ category?: string; season?: string }> }) {
  const user = await requireProfile();
  const { category, season } = await searchParams;
  const uuid = (v?: string) => (v && /^[0-9a-f-]{36}$/i.test(v) ? v : null);
  const page = await standingsPage(user, uuid(category), uuid(season));
  const chosen = page.categories.find((c) => c.id === page.categoryId);
  const thisSeason = page.seasons.find((s) => s.id === page.seasonId);
  // Links keep the season unless it is the current one.
  const seasonQuery = thisSeason && !thisSeason.current ? `season=${thisSeason.id}&` : "";
  const leader = page.drivers[0]?.points ?? 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="page-title flex-1">Standings{thisSeason && !thisSeason.current ? ` · ${thisSeason.name}` : ""}</h1>
        {page.seasons.length > 1 &&
          page.seasons.map((s) => (
            <Link key={s.id} href={s.current ? "/standings" : `/standings?season=${s.id}`} className={s.id === page.seasonId ? "btn-gold px-3 py-1 text-xs" : "btn-ghost px-3 py-1 text-xs"}>
              {s.name}
            </Link>
          ))}
      </div>

      {page.categories.length === 0 && <p className="card text-sm text-snow-faint">This season has no race categories yet.</p>}
      {page.categories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {page.categories.map((c) => {
            const on = c.id === page.categoryId;
            return (
              <Link
                key={c.id}
                href={`/standings?${seasonQuery}category=${c.id}`}
                className="chip"
                style={on ? { backgroundColor: c.color, borderColor: c.color, color: "#0B0B0C" } : { borderColor: `${c.color}80`, color: c.color }}
                title={c.name}
              >
                {c.code}
              </Link>
            );
          })}
        </div>
      )}

      {chosen && (
        <>
          <section className="card space-y-3">
            <div className="flex items-baseline justify-between gap-2">
              <h2 className="font-semibold">{chosen.name} · drivers</h2>
              {page.sessions.length > 0 && <span className="text-xs text-snow-faint">{page.sessions.length} sessions with results</span>}
            </div>
            {page.drivers.length === 0 ? (
              <p className="text-sm text-snow-faint">No results yet.</p>
            ) : (
              <div className="-mx-5 overflow-x-auto px-5">
                <table className="w-full text-sm">
                  <thead className="text-xs text-snow-faint">
                    <tr className="border-b border-night-line">
                      <th className="sticky left-0 bg-night-panel px-2 py-1.5 text-left">Pos</th>
                      <th className="sticky left-11 bg-night-panel px-2 py-1.5 text-left">Driver</th>
                      {page.sessions.map((s, i) => (
                        <RoundHead key={s.id} s={s} i={i} />
                      ))}
                      <th className="px-2 py-1.5 text-right" title="Wins">W</th>
                      <th className="px-2 py-1.5 text-right" title="Podiums">Pod</th>
                      <th className="px-2 py-1.5 text-right">Pts</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-night-line">
                    {page.drivers.map((d, i) => (
                      <tr key={d.key}>
                        <td className="sticky left-0 bg-night-panel px-2 py-2">
                          <Place n={i + 1} />
                        </td>
                        <td className="sticky left-11 min-w-44 bg-night-panel px-2 py-2">
                          <span className="block font-medium">
                            {d.carNumber && <span className="mr-1.5 font-mono text-xs text-snow-faint">#{d.carNumber}</span>}
                            {d.name}
                          </span>
                          <span className="block text-xs text-snow-faint">
                            {d.teamName ?? "No team"}
                            {i > 0 && leader > d.points ? ` · −${Math.round((leader - d.points) * 100) / 100}` : ""}
                          </span>
                        </td>
                        {page.sessions.map((s) => (
                          <RoundCell key={s.id} r={d.rounds[s.id]} />
                        ))}
                        <td className="px-2 py-2 text-right">{d.wins}</td>
                        <td className="px-2 py-2 text-right">{d.podiums}</td>
                        <td className="px-2 py-2 text-right text-base font-bold">{d.points}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {page.teams.length > 0 && (
            <section className="card space-y-3">
              <h2 className="font-semibold">{chosen.name} · teams</h2>
              <div className="-mx-5 overflow-x-auto px-5">
                <table className="w-full text-sm">
                  <thead className="text-xs text-snow-faint">
                    <tr className="border-b border-night-line">
                      <th className="sticky left-0 bg-night-panel px-2 py-1.5 text-left">Pos</th>
                      <th className="sticky left-11 bg-night-panel px-2 py-1.5 text-left">Team</th>
                      {page.sessions.map((s, i) => (
                        <RoundHead key={s.id} s={s} i={i} />
                      ))}
                      <th className="px-2 py-1.5 text-right" title="Wins">W</th>
                      <th className="px-2 py-1.5 text-right" title="Podiums">Pod</th>
                      <th className="px-2 py-1.5 text-right">Pts</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-night-line">
                    {page.teams.map((t, i) => (
                      <tr key={t.id}>
                        <td className="sticky left-0 bg-night-panel px-2 py-2">
                          <Place n={i + 1} />
                        </td>
                        <td className="sticky left-11 min-w-44 bg-night-panel px-2 py-2 font-medium">{t.name}</td>
                        {page.sessions.map((s) => (
                          <td key={s.id} className="px-2 py-2 text-center">
                            {t.rounds[s.id] ?? <span className="text-snow-faint">·</span>}
                          </td>
                        ))}
                        <td className="px-2 py-2 text-right">{t.wins}</td>
                        <td className="px-2 py-2 text-right">{t.podiums}</td>
                        <td className="px-2 py-2 text-right text-base font-bold">{t.points}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {page.sessions.length > 0 && (
            <section className="card space-y-2">
              <h2 className="font-semibold">Sessions</h2>
              <ul className="divide-y divide-night-line">
                {[...page.sessions].reverse().map((s) => (
                  <li key={s.id}>
                    <Link href={`/results/${s.id}`} className="flex items-center gap-3 py-2 text-sm hover:text-gold">
                      <span className="w-9 shrink-0 font-mono text-xs text-snow-faint">R{page.sessions.indexOf(s) + 1}</span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">{s.name}</span>
                        <span className="block truncate text-xs text-snow-faint">
                          {s.weekendName} · <LocalTime iso={s.startsAt} mode="date" />
                        </span>
                      </span>
                      <span className="text-xs text-snow-faint">{s.rows} drivers ›</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}
