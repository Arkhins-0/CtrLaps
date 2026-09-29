"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { ResultRow, ResultStatus } from "@/lib/results";

const STATUS_LABEL: Record<ResultStatus, string> = { finished: "Finished", dnf: "DNF", dns: "DNS", dsq: "DSQ" };

type Draft = { position: string; status: ResultStatus; carNumber: string; driverName: string; teamId: string; points: string; bestLap: string };

const toDraft = (r: ResultRow): Draft => ({
  position: r.position ? String(r.position) : "",
  status: r.status,
  carNumber: r.carNumber,
  driverName: r.driverName,
  teamId: r.teamId ?? "",
  points: r.points ? String(r.points) : "",
  bestLap: r.bestLap,
});

const blank = (position: number): Draft => ({ position: String(position), status: "finished", carNumber: "", driverName: "", teamId: "", points: "", bestLap: "" });

/**
 * A session's results as a table; for those who may, an editor: one row per driver (position, car, driver, team,
 * points, best lap, or DNF / DNS / DSQ). Saving replaces the list; finishers are kept in position order.
 */
export function ResultsEditor({
  sessionId,
  initial,
  canEdit,
  teams,
}: {
  sessionId: string;
  initial: ResultRow[];
  canEdit: boolean;
  teams: { id: string; name: string; entered: boolean }[];
}) {
  const router = useRouter();
  const [results, setResults] = useState(initial);
  const [rows, setRows] = useState<Draft[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (i: number, patch: Partial<Draft>) => setRows((all) => all && all.map((r, j) => (j === i ? { ...r, ...patch } : r)));

  const save = async () => {
    if (!rows) return;
    const kept = rows.filter((r) => r.driverName.trim());
    // Finishers by position, then the rest as they were.
    const ordered = [
      ...kept.filter((r) => r.status === "finished").sort((a, b) => (Number(a.position) || 999) - (Number(b.position) || 999)),
      ...kept.filter((r) => r.status !== "finished"),
    ];
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ results: ResultRow[] }>(`/api/sessions/${sessionId}/results`, {
        method: "PUT",
        json: {
          results: ordered.map((d) => ({
            position: d.status === "finished" ? Number(d.position) || null : null,
            status: d.status,
            carNumber: d.carNumber,
            driverName: d.driverName,
            teamId: d.teamId || null,
            points: Number(d.points) || 0,
            bestLap: d.bestLap,
          })),
        },
      });
      setResults(r.results);
      setRows(null);
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  };

  if (rows) {
    const entered = teams.filter((t) => t.entered);
    const others = teams.filter((t) => !t.entered);
    return (
      <section className="card space-y-3">
        <h2 className="font-semibold">Enter results</h2>
        <p className="text-xs text-snow-faint">
          One row per driver. A driver whose name matches a racer&apos;s account is linked to them. Points are as your series scores them.
        </p>
        {error && <p className="error">{error}</p>}
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full min-w-[46rem] text-sm">
            <thead className="text-left text-xs text-snow-faint">
              <tr>
                <th className="px-1 py-1">Pos</th>
                <th className="px-1 py-1">Status</th>
                <th className="px-1 py-1">Car</th>
                <th className="px-1 py-1">Driver</th>
                <th className="px-1 py-1">Team</th>
                <th className="px-1 py-1">Points</th>
                <th className="px-1 py-1">Best lap</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={i}>
                  <td className="px-1 py-1">
                    <input
                      className="input w-14 px-2 py-1"
                      inputMode="numeric"
                      value={r.status === "finished" ? r.position : ""}
                      disabled={r.status !== "finished"}
                      onChange={(e) => set(i, { position: e.target.value.replace(/\D/g, "") })}
                      aria-label="Position"
                    />
                  </td>
                  <td className="px-1 py-1">
                    <select className="input w-24 px-2 py-1" value={r.status} onChange={(e) => set(i, { status: e.target.value as ResultStatus })} aria-label="Status">
                      {(Object.keys(STATUS_LABEL) as ResultStatus[]).map((s) => (
                        <option key={s} value={s}>
                          {STATUS_LABEL[s]}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="px-1 py-1">
                    <input className="input w-16 px-2 py-1" value={r.carNumber} maxLength={10} onChange={(e) => set(i, { carNumber: e.target.value })} aria-label="Car number" />
                  </td>
                  <td className="px-1 py-1">
                    <input className="input min-w-40 px-2 py-1" value={r.driverName} maxLength={120} onChange={(e) => set(i, { driverName: e.target.value })} aria-label="Driver" />
                  </td>
                  <td className="px-1 py-1">
                    <select className="input w-44 px-2 py-1" value={r.teamId} onChange={(e) => set(i, { teamId: e.target.value })} aria-label="Team">
                      <option value="">No team</option>
                      {entered.length > 0 && (
                        <optgroup label="Entered in this category">
                          {entered.map((t) => (
                            <option key={t.id} value={t.id}>
                              {t.name}
                            </option>
                          ))}
                        </optgroup>
                      )}
                      {others.length > 0 && (
                        <optgroup label="Other teams">
                          {others.map((t) => (
                            <option key={t.id} value={t.id}>
                              {t.name}
                            </option>
                          ))}
                        </optgroup>
                      )}
                    </select>
                  </td>
                  <td className="px-1 py-1">
                    <input className="input w-16 px-2 py-1" inputMode="decimal" value={r.points} onChange={(e) => set(i, { points: e.target.value.replace(/[^\d.]/g, "") })} aria-label="Points" />
                  </td>
                  <td className="px-1 py-1">
                    <input className="input w-24 px-2 py-1" value={r.bestLap} maxLength={20} placeholder="1:42.315" onChange={(e) => set(i, { bestLap: e.target.value })} aria-label="Best lap" />
                  </td>
                  <td className="px-1 py-1">
                    <button type="button" className="btn-icon text-danger/80 hover:text-danger" aria-label="Remove row" onClick={() => setRows(rows.filter((_, j) => j !== i))}>
                      ×
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex flex-wrap justify-between gap-2">
          <button type="button" className="btn-ghost px-3 py-1.5 text-xs" onClick={() => setRows([...rows, blank(rows.filter((r) => r.status === "finished").length + 1)])} disabled={busy}>
            Add driver
          </button>
          <div className="flex gap-2">
            <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={() => { setRows(null); setError(null); }} disabled={busy}>
              Cancel
            </button>
            <button type="button" className="btn-gold px-4 py-1.5 text-xs" onClick={save} disabled={busy}>
              {busy ? "Saving…" : "Save results"}
            </button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="card space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-semibold">Results</h2>
        {canEdit && (
          <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => setRows(results.length > 0 ? results.map(toDraft) : [blank(1), blank(2), blank(3)])}>
            {results.length > 0 ? "Edit results" : "Enter results"}
          </button>
        )}
      </div>
      {results.length === 0 ? (
        <p className="text-sm text-snow-faint">No results yet.</p>
      ) : (
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-snow-faint">
              <tr>
                <th className="px-2 py-1.5">Pos</th>
                <th className="px-2 py-1.5">Driver</th>
                <th className="px-2 py-1.5">Team</th>
                <th className="px-2 py-1.5 text-right">Best lap</th>
                <th className="px-2 py-1.5 text-right">Points</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-night-line">
              {results.map((r, i) => (
                <tr key={i}>
                  <td className="px-2 py-2 text-snow-soft">{r.status === "finished" ? (r.position ?? "–") : STATUS_LABEL[r.status]}</td>
                  <td className="px-2 py-2">
                    {r.carNumber && <span className="mr-1.5 font-mono text-xs text-snow-faint">#{r.carNumber}</span>}
                    {r.driverName}
                  </td>
                  <td className="px-2 py-2 text-snow-soft">{r.teamName ?? ""}</td>
                  <td className="px-2 py-2 text-right font-mono text-xs">{r.bestLap}</td>
                  <td className="px-2 py-2 text-right font-semibold">{r.points || ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
