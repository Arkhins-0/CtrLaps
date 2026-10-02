"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { api } from "@/lib/client";
import type { EntrantTeam, ResultRow, ResultStatus } from "@/lib/results";
import { rowPoints, scoringSummary, type Scoring } from "@/lib/scoring";

const STATUS_LABEL: Record<ResultStatus, string> = { finished: "Finished", dnf: "DNF", dns: "DNS", dsq: "DSQ" };
const TYPED = "__typed";

/** One row being edited. `userId` is the racer picked; null with `typed` for a name typed in (a late entry). */
type Draft = {
  key: string;
  status: ResultStatus;
  teamId: string;
  userId: string | null;
  typed: boolean;
  driverName: string;
  carNumber: string;
  bestLap: string;
  pole: boolean;
  fastestLap: boolean;
  points: string;
  /** Points typed by hand: the table no longer fills them in. */
  manual: boolean;
};

let seq = 0;
const newKey = () => `r${++seq}`;

const fromRow = (r: ResultRow): Draft => ({
  key: newKey(),
  status: r.status,
  teamId: r.teamId ?? "",
  userId: r.userId,
  typed: !r.userId,
  driverName: r.driverName,
  carNumber: r.carNumber,
  bestLap: r.bestLap,
  pole: r.pole,
  fastestLap: r.fastestLap,
  points: r.points ? String(r.points) : "",
  manual: r.manualPoints,
});

const blank = (): Draft => ({ key: newKey(), status: "finished", teamId: "", userId: null, typed: false, driverName: "", carNumber: "", bestLap: "", pole: false, fastestLap: false, points: "", manual: false });

/** Finishers first in their order (that order is the result), then DNF, DNS and DSQ. */
const sorted = (rows: Draft[]) => [...rows.filter((r) => r.status === "finished"), ...rows.filter((r) => r.status !== "finished")];

/**
 * A session's results. Everyone sees the classification; admins, coordinators and the category's race officials edit
 * it: the list is the finishing order (drag a row, or use the arrows), each row a team and then one of its racers (or
 * a name typed in for a late entry), the car, DNF / DNS / DSQ, pole, fastest lap, best lap and points. With a points
 * table, points fill in from it and can still be typed over. Saving replaces the list.
 */
export function ResultsEditor({
  sessionId,
  initial,
  canEdit,
  entrants,
  scoring = null,
  scores: initialScores = true,
  notifiedAt = null,
}: {
  sessionId: string;
  initial: ResultRow[];
  canEdit: boolean;
  entrants: EntrantTeam[];
  scoring?: Scoring | null;
  scores?: boolean;
  /** When its results were last sent to the category's followers; null when never. */
  notifiedAt?: string | null;
}) {
  const router = useRouter();
  const [results, setResults] = useState(initial);
  const [rows, setRows] = useState<Draft[] | null>(null);
  const [scores, setScores] = useState(initialScores);
  // Tell the category's followers (push, and email to those who keep it on): on the first time, off for a correction.
  const [sentBefore, setSentBefore] = useState(Boolean(notifiedAt));
  const [notify, setNotify] = useState(!notifiedAt);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dragging = useRef<string | null>(null);
  const [dragKey, setDragKey] = useState<string | null>(null);

  const racer = (id: string | null) => {
    for (const t of entrants) for (const r of t.racers) if (r.id === id) return { ...r, teamId: t.id };
    return null;
  };
  const finishPos = (all: Draft[], key: string) => all.filter((r) => r.status === "finished").findIndex((r) => r.key === key) + 1;

  // Points from the table for every row not typed by hand, with positions as the list now stands.
  const score = (all: Draft[], scored = scores): Draft[] =>
    all.map((r) => {
      if (!scoring || r.manual) return r;
      const n = scored ? rowPoints(scoring, { status: r.status, position: r.status === "finished" ? finishPos(all, r.key) : null, pole: r.pole, fastestLap: r.fastestLap }) : 0;
      return { ...r, points: n ? String(n) : "" };
    });
  const commit = (next: Draft[]) => setRows(score(sorted(next)));
  const set = (key: string, patch: Partial<Draft>) =>
    rows &&
    commit(
      rows.map((r) => {
        if (r.key === key) return { ...r, ...patch };
        // One pole and one fastest lap: ticking one takes it from whoever had it.
        return { ...r, pole: patch.pole ? false : r.pole, fastestLap: patch.fastestLap ? false : r.fastestLap };
      }),
    );
  const move = (key: string, to: number) => {
    if (!rows) return;
    const from = rows.findIndex((r) => r.key === key);
    if (from < 0 || to < 0 || to >= rows.length || from === to) return;
    const next = [...rows];
    const [row] = next.splice(from, 1);
    next.splice(to, 0, row);
    // Dragged among the finishers it stays finished; past them, it keeps its own status (and sorts back).
    commit(next);
  };

  const pickTeam = (key: string, teamId: string) => {
    const r = rows?.find((x) => x.key === key);
    if (!r) return;
    // A racer picked from another team no longer fits: the driver is chosen again.
    const keep = r.typed || (r.userId && racer(r.userId)?.teamId === teamId);
    set(key, keep ? { teamId } : { teamId, userId: null, driverName: "", carNumber: "" });
  };
  const pickDriver = (key: string, value: string) => {
    if (value === TYPED) return set(key, { userId: null, typed: true, driverName: "" });
    const p = racer(value);
    if (!p) return set(key, { userId: null, typed: false, driverName: "" });
    const r = rows?.find((x) => x.key === key);
    set(key, { userId: p.id, typed: false, driverName: p.name, teamId: p.teamId, carNumber: r?.carNumber || p.carNumber });
  };
  const used = new Set(rows?.map((r) => r.userId).filter(Boolean));
  // Everyone in the entered teams not in the list yet.
  const missing = entrants.filter((t) => t.entered).flatMap((t) => t.racers.map((r) => ({ ...r, teamId: t.id }))).filter((r) => !used.has(r.id));
  const addAll = () =>
    rows &&
    commit([
      ...rows.filter((r) => r.userId || r.typed || r.driverName.trim()),
      ...missing.map((m) => ({ ...blank(), teamId: m.teamId, userId: m.id, driverName: m.name, carNumber: m.carNumber })),
    ]);

  const save = async () => {
    if (!rows) return;
    const kept = rows.filter((r) => r.driverName.trim());
    if (kept.length !== rows.length && !confirm("Rows without a driver will be left out. Save?")) return;
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ results: ResultRow[] }>(`/api/sessions/${sessionId}/results`, {
        method: "PUT",
        json: {
          results: kept.map((d) => ({
            position: d.status === "finished" ? finishPos(kept, d.key) : null,
            status: d.status,
            carNumber: d.carNumber,
            driverName: d.driverName.trim(),
            userId: d.userId,
            teamId: d.teamId || null,
            points: Number(d.points) || 0,
            bestLap: d.bestLap,
            pole: d.pole,
            fastestLap: d.fastestLap,
            manualPoints: d.manual || !scoring,
          })),
          scores,
          notify,
        },
      });
      if (notify) setSentBefore(true);
      setNotify(false);
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
    const entered = entrants.filter((t) => t.entered);
    const others = entrants.filter((t) => !t.entered);
    return (
      <section className="card space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="flex-1 font-semibold">Enter results</h2>
          {missing.length > 0 && (
            <button type="button" className="btn-ghost px-3 py-1.5 text-xs" onClick={addAll} disabled={busy}>
              Add all entrants · {missing.length}
            </button>
          )}
        </div>
        <p className="text-xs text-snow-faint">
          The list is the finishing order: drag a row by its handle, or use the arrows. Pick each driver&apos;s team, then the racer; for someone without an
          account, choose <span className="text-snow-soft">Type a name</span>. DNF, DNS and DSQ go to the bottom.
        </p>
        {scoring && (
          <label className="flex flex-wrap items-center gap-2 rounded-xl border border-night-line px-3 py-2 text-sm">
            <input
              type="checkbox"
              className="h-4 w-4 accent-gold"
              checked={scores}
              onChange={(e) => {
                setScores(e.target.checked);
                setRows(score(rows, e.target.checked));
              }}
            />
            Points from the table
            <span className="text-xs text-snow-faint">{scoringSummary(scoring)}</span>
          </label>
        )}
        {error && <p className="error">{error}</p>}

        <ol className="space-y-2">
          {rows.map((r, i) => {
            const pos = r.status === "finished" ? finishPos(rows, r.key) : null;
            const team = entrants.find((t) => t.id === r.teamId);
            const choices = r.teamId ? (team?.racers ?? []) : entrants.flatMap((t) => t.racers);
            return (
              <li
                key={r.key}
                className={`rounded-2xl border bg-night-panel/60 p-2 transition ${dragKey === r.key ? "border-gold/60 opacity-60" : "border-night-line"}`}
                onDragOver={(e) => {
                  e.preventDefault();
                  const from = dragging.current;
                  if (from && from !== r.key) move(from, i);
                }}
                onDrop={(e) => e.preventDefault()}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    draggable
                    onDragStart={(e) => {
                      dragging.current = r.key;
                      setDragKey(r.key);
                      e.dataTransfer.effectAllowed = "move";
                    }}
                    onDragEnd={() => {
                      dragging.current = null;
                      setDragKey(null);
                    }}
                    className="cursor-grab select-none px-1 text-lg leading-none text-snow-faint active:cursor-grabbing"
                    title="Drag to reorder"
                    aria-hidden
                  >
                    ⠿
                  </span>
                  <span
                    className={`flex h-8 min-w-8 shrink-0 items-center justify-center rounded-lg px-1.5 text-sm font-bold ${
                      pos === null ? "bg-danger/15 text-danger" : pos <= 3 ? "bg-gold text-night" : "bg-night-line text-snow"
                    }`}
                  >
                    {pos ?? STATUS_LABEL[r.status]}
                  </span>
                  <select className="input w-40 flex-1 px-2 py-1.5 text-sm" value={r.teamId} onChange={(e) => pickTeam(r.key, e.target.value)} aria-label="Team" disabled={busy}>
                    <option value="">Team…</option>
                    <optgroup label="Entered in this category">
                      {entered.map((t) => (
                        <option key={t.id || "none"} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </optgroup>
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
                  {r.typed ? (
                    <span className="flex min-w-44 flex-[2] items-center gap-1">
                      <input
                        className="input min-w-0 flex-1 px-2 py-1.5 text-sm"
                        placeholder="Driver's full name"
                        value={r.driverName}
                        maxLength={120}
                        onChange={(e) => set(r.key, { driverName: e.target.value })}
                        aria-label="Driver"
                        autoFocus={!r.driverName}
                      />
                      <button type="button" className="btn-icon text-xs text-snow-faint" title="Pick from the list" onClick={() => set(r.key, { typed: false, driverName: "" })}>
                        ▾
                      </button>
                    </span>
                  ) : (
                    <select className="input min-w-44 flex-[2] px-2 py-1.5 text-sm" value={r.userId ?? ""} onChange={(e) => pickDriver(r.key, e.target.value)} aria-label="Driver" disabled={busy}>
                      <option value="">Driver…</option>
                      {choices.map((p) => (
                        <option key={p.id} value={p.id} disabled={used.has(p.id) && p.id !== r.userId}>
                          {p.name}
                          {p.carNumber ? ` · #${p.carNumber}` : ""}
                        </option>
                      ))}
                      <option value={TYPED}>+ Type a name…</option>
                    </select>
                  )}
                  <input className="input w-16 px-2 py-1.5 text-sm" placeholder="Car" value={r.carNumber} maxLength={10} onChange={(e) => set(r.key, { carNumber: e.target.value })} aria-label="Car number" />
                  <span className="ml-auto flex items-center">
                    <button type="button" className="btn-icon" aria-label="Move up" onClick={() => move(r.key, i - 1)} disabled={busy || i === 0}>
                      ↑
                    </button>
                    <button type="button" className="btn-icon" aria-label="Move down" onClick={() => move(r.key, i + 1)} disabled={busy || i === rows.length - 1}>
                      ↓
                    </button>
                    <button type="button" className="btn-icon text-danger/80 hover:text-danger" aria-label="Remove row" onClick={() => commit(rows.filter((x) => x.key !== r.key))} disabled={busy}>
                      ×
                    </button>
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2 pl-7 text-sm">
                  <select className="input w-28 px-2 py-1 text-sm" value={r.status} onChange={(e) => set(r.key, { status: e.target.value as ResultStatus })} aria-label="Status">
                    {(Object.keys(STATUS_LABEL) as ResultStatus[]).map((s) => (
                      <option key={s} value={s}>
                        {STATUS_LABEL[s]}
                      </option>
                    ))}
                  </select>
                  <button type="button" className={`chip px-2.5 py-0.5 text-xs ${r.pole ? "border-gold bg-gold text-night" : ""}`} onClick={() => set(r.key, { pole: !r.pole })} aria-pressed={r.pole}>
                    Pole
                  </button>
                  <button type="button" className={`chip px-2.5 py-0.5 text-xs ${r.fastestLap ? "border-gold bg-gold text-night" : ""}`} onClick={() => set(r.key, { fastestLap: !r.fastestLap })} aria-pressed={r.fastestLap}>
                    Fastest lap
                  </button>
                  <input className="input w-28 px-2 py-1 text-sm" placeholder="Best lap" value={r.bestLap} maxLength={20} onChange={(e) => set(r.key, { bestLap: e.target.value })} aria-label="Best lap" />
                  <span className="ml-auto flex items-center gap-1">
                    <span className="text-xs text-snow-faint">{scoring && !r.manual ? "Points (table)" : "Points"}</span>
                    <input
                      className={`input w-16 px-2 py-1 text-right text-sm ${scoring && !r.manual ? "text-snow-soft" : "font-semibold"}`}
                      inputMode="decimal"
                      value={r.points}
                      onChange={(e) => set(r.key, { points: e.target.value.replace(/[^\d.]/g, ""), manual: true })}
                      aria-label="Points"
                    />
                    {scoring && r.manual && (
                      <button type="button" className="btn-icon text-sm text-gold" title="Use the table's points" aria-label="Use the table's points" onClick={() => set(r.key, { manual: false })}>
                        ↺
                      </button>
                    )}
                  </span>
                </div>
              </li>
            );
          })}
        </ol>

        <div className="flex flex-wrap items-center gap-2">
          <button type="button" className="btn-ghost px-3 py-1.5 text-xs" onClick={() => commit([...rows.filter((r) => r.status === "finished"), blank(), ...rows.filter((r) => r.status !== "finished")])} disabled={busy}>
            + Add driver
          </button>
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2 text-xs text-snow-soft" title="A push to everyone following this category, and an email to those who keep results email on">
              <input type="checkbox" className="h-4 w-4 accent-gold" checked={notify} onChange={(e) => setNotify(e.target.checked)} />
              {sentBefore ? "Notify again" : "Notify followers"}
            </label>
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
        <h2 className="font-semibold">Classification</h2>
        {canEdit && (
          <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => setRows(score(results.length > 0 ? results.map(fromRow) : [blank(), blank(), blank()]))}>
            {results.length > 0 ? "Edit results" : "Enter results"}
          </button>
        )}
      </div>
      {results.length === 0 ? (
        <p className="text-sm text-snow-faint">No results yet.</p>
      ) : (
        <ResultsTable results={results} />
      )}
    </section>
  );
}

/** The classification: position (gold for the podium), car, driver with pole and fastest-lap marks, team, best lap, points. */
function ResultsTable({ results }: { results: ResultRow[] }) {
  return (
    <div className="-mx-2 overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="text-left text-xs text-snow-faint">
          <tr>
            <th className="px-2 py-1.5">Pos</th>
            <th className="px-2 py-1.5">Driver</th>
            <th className="hidden px-2 py-1.5 sm:table-cell">Team</th>
            <th className="px-2 py-1.5 text-right">Best lap</th>
            <th className="px-2 py-1.5 text-right">Pts</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-night-line">
          {results.map((r, i) => (
            <tr key={i}>
              <td className="px-2 py-2">
                <span
                  className={`inline-flex h-7 min-w-7 items-center justify-center rounded-md px-1 text-xs font-bold ${
                    r.status !== "finished" ? "bg-danger/15 text-danger" : (r.position ?? 99) <= 3 ? "bg-gold text-night" : "bg-night-line"
                  }`}
                >
                  {r.status === "finished" ? (r.position ?? "–") : STATUS_LABEL[r.status]}
                </span>
              </td>
              <td className="px-2 py-2">
                <span className="flex flex-wrap items-center gap-1.5">
                  {r.carNumber && <span className="font-mono text-xs text-snow-faint">#{r.carNumber}</span>}
                  <span className="font-medium">{r.driverName}</span>
                  {r.pole && <span className="chip px-1.5 py-0 text-[10px]" title="Pole position">P</span>}
                  {r.fastestLap && <span className="chip border-gold/40 px-1.5 py-0 text-[10px] text-gold" title="Fastest lap">FL</span>}
                </span>
                {r.teamName && <span className="block text-xs text-snow-faint sm:hidden">{r.teamName}</span>}
              </td>
              <td className="hidden px-2 py-2 text-snow-soft sm:table-cell">{r.teamName ?? ""}</td>
              <td className="px-2 py-2 text-right font-mono text-xs">{r.bestLap}</td>
              <td className="px-2 py-2 text-right font-semibold">{r.points || ""}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
