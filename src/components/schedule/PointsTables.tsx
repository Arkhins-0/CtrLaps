"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { Category } from "@/lib/categories";
import { DEFAULT_SCORING, pointsList, pointsText, scoringSummary, type Scoring } from "@/lib/scoring";
import { CategoryTag } from "./CategoryTag";

type Form = { points: string; dnf: string; dns: string; dsq: string; pole: string; fastestLap: string };

const formOf = (s: Scoring): Form => ({
  points: pointsText(s.points),
  dnf: String(s.dnf),
  dns: String(s.dns),
  dsq: String(s.dsq),
  pole: String(s.pole),
  fastestLap: String(s.fastestLap),
});

/**
 * Admin: each category's points table — points by finishing position, for a DNF, DNS or DSQ, and the pole and
 * fastest-lap bonuses. Results then score from it (points typed by hand stay as typed). A category without one has
 * its points typed for every result.
 */
export function PointsTables({ categories }: { categories: Category[] }) {
  const router = useRouter();
  const [tables, setTables] = useState<Record<string, Scoring | null>>(Object.fromEntries(categories.map((c) => [c.id, c.scoring])));
  const [editing, setEditing] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(formOf(DEFAULT_SCORING));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async (id: string, scoring: Scoring | null) => {
    if (scoring === null && !confirm("Remove this points table? Points already worked out stay; new ones are typed by hand.")) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/api/categories/${id}/scoring`, { method: "PUT", json: { scoring } });
      setTables((t) => ({ ...t, [id]: scoring }));
      setEditing(null);
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  };

  if (categories.length === 0) return null;
  return (
    <section className="space-y-2 border-t border-night-line pt-4">
      <h3 className="font-semibold">Points tables</h3>
      <p className="text-xs text-snow-faint">
        Points for each finishing position, a DNF, DNS or DSQ, and the pole and fastest-lap bonuses. Race results fill in from it; qualifying and
        practice don&apos;t score unless switched on. Changing a table re-scores results that weren&apos;t typed by hand.
      </p>
      {error && <p className="error">{error}</p>}
      <ul className="space-y-2">
        {categories.map((c) => {
          const t = tables[c.id];
          return (
            <li key={c.id} className="space-y-2 rounded-xl border border-night-line p-2">
              <div className="flex flex-wrap items-center gap-2">
                <CategoryTag category={c} title={false} />
                <span className="min-w-0 flex-1 truncate text-sm text-snow-soft">{t ? scoringSummary(t) : "No table: points are typed"}</span>
                {editing !== c.id && (
                  <button
                    type="button"
                    className="btn-ghost px-3 py-1 text-xs"
                    disabled={busy}
                    onClick={() => {
                      setForm(formOf(t ?? DEFAULT_SCORING));
                      setEditing(c.id);
                    }}
                  >
                    {t ? "Edit" : "Add table"}
                  </button>
                )}
              </div>
              {editing === c.id && (
                <div className="space-y-2">
                  <label className="block">
                    <span className="label">Points for 1st, 2nd, 3rd… (commas between)</span>
                    <input className="input py-1.5 font-mono text-sm" value={form.points} onChange={(e) => setForm({ ...form, points: e.target.value })} />
                  </label>
                  <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
                    {(
                      [
                        ["dnf", "DNF"],
                        ["dns", "DNS"],
                        ["dsq", "DSQ"],
                        ["pole", "Pole bonus"],
                        ["fastestLap", "Fastest lap bonus"],
                      ] as [keyof Form, string][]
                    ).map(([k, label]) => (
                      <label key={k} className="block">
                        <span className="label">{label}</span>
                        <input className="input py-1.5 text-sm" inputMode="decimal" value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value.replace(/[^\d.]/g, "") })} />
                      </label>
                    ))}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {t && (
                      <button type="button" className="btn-ghost mr-auto px-3 py-1 text-xs text-danger" disabled={busy} onClick={() => save(c.id, null)}>
                        Remove table
                      </button>
                    )}
                    <button type="button" className="btn-ghost ml-auto px-3 py-1 text-xs" disabled={busy} onClick={() => setEditing(null)}>
                      Cancel
                    </button>
                    <button
                      type="button"
                      className="btn-gold px-3 py-1 text-xs"
                      disabled={busy}
                      onClick={() =>
                        save(c.id, {
                          points: pointsList(form.points),
                          dnf: Number(form.dnf) || 0,
                          dns: Number(form.dns) || 0,
                          dsq: Number(form.dsq) || 0,
                          pole: Number(form.pole) || 0,
                          fastestLap: Number(form.fastestLap) || 0,
                        })
                      }
                    >
                      {busy ? "Saving…" : "Save table"}
                    </button>
                  </div>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
