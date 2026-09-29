import Link from "next/link";
import { LocalTime } from "@/components/LocalTime";
import { standingsPage } from "@/lib/results";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Standings" };

/** This season's standings, one race category at a time: drivers, teams, and the sessions with results. */
export default async function Standings({ searchParams }: { searchParams: Promise<{ category?: string }> }) {
  const user = await requireProfile();
  const { category } = await searchParams;
  const page = await standingsPage(user, category && /^[0-9a-f-]{36}$/i.test(category) ? category : null);
  const chosen = page.categories.find((c) => c.id === page.categoryId);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-2">
        <h1 className="page-title">Standings</h1>
        <Link href="/schedule" className="btn-ghost px-3 py-1.5 text-xs">
          Schedule
        </Link>
      </div>
      {page.categories.length === 0 && <p className="card text-sm text-snow-faint">This season has no race categories yet.</p>}
      {page.categories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {page.categories.map((c) => {
            const on = c.id === page.categoryId;
            return (
              <Link
                key={c.id}
                href={`/standings?category=${c.id}`}
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
            <h2 className="font-semibold">{chosen.name} · drivers</h2>
            {page.drivers.length === 0 ? (
              <p className="text-sm text-snow-faint">No results yet.</p>
            ) : (
              <div className="-mx-2 overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="text-left text-xs text-snow-faint">
                    <tr>
                      <th className="px-2 py-1.5">#</th>
                      <th className="px-2 py-1.5">Driver</th>
                      <th className="px-2 py-1.5">Team</th>
                      <th className="px-2 py-1.5 text-right">Wins</th>
                      <th className="px-2 py-1.5 text-right">Points</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-night-line">
                    {page.drivers.map((d, i) => (
                      <tr key={d.key}>
                        <td className="px-2 py-2 text-snow-faint">{i + 1}</td>
                        <td className="px-2 py-2">
                          {d.carNumber && <span className="mr-1.5 font-mono text-xs text-snow-faint">#{d.carNumber}</span>}
                          {d.name}
                        </td>
                        <td className="px-2 py-2 text-snow-soft">{d.teamName ?? ""}</td>
                        <td className="px-2 py-2 text-right">{d.wins}</td>
                        <td className="px-2 py-2 text-right font-semibold">{d.points}</td>
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
              <div className="-mx-2 overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="text-left text-xs text-snow-faint">
                    <tr>
                      <th className="px-2 py-1.5">#</th>
                      <th className="px-2 py-1.5">Team</th>
                      <th className="px-2 py-1.5 text-right">Wins</th>
                      <th className="px-2 py-1.5 text-right">Points</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-night-line">
                    {page.teams.map((t, i) => (
                      <tr key={t.id}>
                        <td className="px-2 py-2 text-snow-faint">{i + 1}</td>
                        <td className="px-2 py-2">{t.name}</td>
                        <td className="px-2 py-2 text-right">{t.wins}</td>
                        <td className="px-2 py-2 text-right font-semibold">{t.points}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
          {page.sessions.length > 0 && (
            <section className="card space-y-2">
              <h2 className="font-semibold">Results</h2>
              <ul className="divide-y divide-night-line">
                {page.sessions.map((s) => (
                  <li key={s.id}>
                    <Link href={`/results/${s.id}`} className="flex items-center gap-3 py-2 text-sm hover:text-gold">
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
