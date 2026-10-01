"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Icon } from "@/components/Icon";
import { api } from "@/lib/client";
import type { Category } from "@/lib/categories";
import { CATEGORY_COLORS } from "@/lib/categoryColors";
import type { Season } from "@/lib/seasons";
import { CategoryTag } from "./CategoryTag";
import { PointsTables } from "./PointsTables";

type Row = { key: string; id: string | null; name: string; code: string; color: string };

const rowsOf = (list: Category[]): Row[] => list.map((c) => ({ key: c.id, id: c.id, name: c.name, code: c.code, color: c.color }));

/**
 * The admin's race categories for a season: name, short code and colour for each, in order. Saving sends the whole
 * list; one taken out is removed, and its sessions become for everyone.
 */
export function CategoriesEditor({ seasons, categories, onClose }: { seasons: Season[]; categories: Category[]; onClose: () => void }) {
  const router = useRouter();
  const [seasonId, setSeasonId] = useState(seasons.find((s) => s.current)?.id ?? seasons[0]?.id ?? "");
  const [rows, setRows] = useState<Row[]>(rowsOf(categories.filter((c) => c.seasonId === seasonId)));
  // The season's saved categories, for their points tables.
  const [saved, setSaved] = useState<Category[]>(categories.filter((c) => c.seasonId === seasonId));
  const [picking, setPicking] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const switchSeason = async (id: string) => {
    setSeasonId(id);
    setError(null);
    try {
      const r = await api<{ categories: Category[] }>(`/api/seasons/${id}/categories`);
      setRows(rowsOf(r.categories));
      setSaved(r.categories);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load the categories.");
    }
  };

  const update = (key: string, patch: Partial<Row>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const move = (i: number, by: number) =>
    setRows((rs) => {
      const j = i + by;
      if (j < 0 || j >= rs.length) return rs;
      const next = [...rs];
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });
  const add = () =>
    setRows((rs) => [
      ...rs,
      { key: `new-${Date.now()}`, id: null, name: "", code: "", color: CATEGORY_COLORS.find((c) => !rs.some((r) => r.color === c)) ?? CATEGORY_COLORS[0] },
    ]);

  const save = async () => {
    const removed = categories.filter((c) => c.seasonId === seasonId && !rows.some((r) => r.id === c.id));
    if (removed.length > 0 && !confirm(`Remove ${removed.map((c) => c.code).join(", ")}? Their sessions become for everyone.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/api/seasons/${seasonId}/categories`, {
        method: "PUT",
        json: { categories: rows.map((r) => ({ id: r.id, name: r.name.trim(), code: r.code.trim(), color: r.color })) },
      });
      router.refresh();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <section className="card space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="flex-1 font-semibold">Race categories</h2>
        {seasons.length > 1 && (
          <select className="input w-auto py-1.5 text-sm" value={seasonId} onChange={(e) => switchSeason(e.target.value)} disabled={busy}>
            {seasons.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.current ? " (current)" : ""}
              </option>
            ))}
          </select>
        )}
      </div>
      <p className="text-xs text-snow-faint">
        The classes racing this season. Weekends say which of them run each round, and each session can belong to one.
      </p>
      {error && <p className="error">{error}</p>}
      {rows.length === 0 && <p className="text-sm text-snow-faint">No categories yet.</p>}
      <ul className="space-y-2">
        {rows.map((r, i) => (
          <li key={r.key} className="space-y-2 rounded-xl border border-night-line p-2">
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                className="h-8 w-8 shrink-0 rounded-full border-2 border-night-line"
                style={{ backgroundColor: r.color }}
                aria-label="Colour"
                onClick={() => setPicking(picking === r.key ? null : r.key)}
                disabled={busy}
              />
              <input
                className="input min-w-0 flex-1 py-1.5 text-sm"
                placeholder="Indian Touring Car"
                value={r.name}
                onChange={(e) => update(r.key, { name: e.target.value })}
                disabled={busy}
              />
              <input
                className="input w-24 py-1.5 font-mono text-sm uppercase"
                placeholder="ITC"
                maxLength={8}
                value={r.code}
                onChange={(e) => update(r.key, { code: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "") })}
                disabled={busy}
              />
              {r.code && <CategoryTag category={r} title={false} />}
              <div className="ml-auto flex">
                <button type="button" className="btn-icon" aria-label="Move up" onClick={() => move(i, -1)} disabled={busy || i === 0}>
                  <span className="text-lg leading-none">↑</span>
                </button>
                <button type="button" className="btn-icon" aria-label="Move down" onClick={() => move(i, 1)} disabled={busy || i === rows.length - 1}>
                  <span className="text-lg leading-none">↓</span>
                </button>
                <button
                  type="button"
                  className="btn-icon text-danger/80 hover:text-danger"
                  aria-label={`Remove ${r.name || "category"}`}
                  onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}
                  disabled={busy}
                >
                  <Icon name="trash" className="h-4 w-4" />
                </button>
              </div>
            </div>
            {picking === r.key && (
              <div className="flex flex-wrap gap-2 pl-10">
                {CATEGORY_COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    className={`h-7 w-7 rounded-full border-2 ${r.color === c ? "border-snow" : "border-transparent"}`}
                    style={{ backgroundColor: c }}
                    aria-label={c}
                    onClick={() => {
                      update(r.key, { color: c });
                      setPicking(null);
                    }}
                  />
                ))}
              </div>
            )}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={add} disabled={busy}>
          Add category
        </button>
        <div className="ml-auto flex gap-2">
          <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="btn-gold px-4 py-1.5 text-xs" onClick={save} disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </div>
      <PointsTables key={`${seasonId}:${saved.map((c) => c.id).join()}`} categories={saved} />
    </section>
  );
}
