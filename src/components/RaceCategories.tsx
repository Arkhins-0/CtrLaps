"use client";

import { useState } from "react";
import { api } from "@/lib/client";
import type { Category } from "@/lib/categories";
import type { Role } from "@/lib/roles";
import { CategoryTag } from "./schedule/CategoryTag";

/**
 * A person's race categories on their page: a racer's classes ("Races in", from their team's entries), a race
 * official's ("Looks after"), or for crew and team managers what their team runs. Their manager, a coordinator or an
 * admin taps to change a racer's or an official's.
 */
export function RaceCategories({
  userId,
  role,
  categories,
  initial,
  teamIds,
  canSet,
}: {
  userId: string;
  role: Role;
  categories: Category[];
  initial: string[];
  teamIds: string[];
  canSet: boolean;
}) {
  const [ids, setIds] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const assignable = role === "racer";
  if (categories.length === 0) return null;
  if (!assignable && teamIds.length === 0) return null;

  const offered = role === "racer" && teamIds.length > 0 ? categories.filter((c) => teamIds.includes(c.id)) : categories;
  const title = role === "racer" ? "Races in" : role === "race_official" ? "Looks after" : "Team races in";

  const toggle = async (id: string) => {
    const next = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
    setIds(next);
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ categoryIds: string[] }>(`/api/users/${userId}/categories`, { method: "PUT", json: { categoryIds: next } });
      setIds(r.categoryIds);
    } catch (e) {
      setIds(ids);
      setError(e instanceof Error ? e.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  };

  if (!assignable) {
    return (
      <section className="card space-y-2">
        <p className="label">{title}</p>
        <div className="flex flex-wrap gap-1.5">
          {categories.filter((c) => teamIds.includes(c.id)).map((c) => (
            <CategoryTag key={c.id} category={c} />
          ))}
        </div>
      </section>
    );
  }

  return (
    <section className="card space-y-2">
      <p className="label">{title}</p>
      {error && <p className="error">{error}</p>}
      {canSet ? (
        <div className="flex flex-wrap gap-1.5">
          {offered.map((c) => {
            const on = ids.includes(c.id);
            return (
              <button
                key={c.id}
                className="chip px-2 py-0.5 text-[11px]"
                style={on ? { backgroundColor: c.color, borderColor: c.color, color: "#0B0B0C" } : { borderColor: `${c.color}66`, color: c.color }}
                title={c.name}
                onClick={() => toggle(c.id)}
                disabled={busy}
                aria-pressed={on}
              >
                {c.code}
              </button>
            );
          })}
        </div>
      ) : ids.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {categories.filter((c) => ids.includes(c.id)).map((c) => (
            <CategoryTag key={c.id} category={c} />
          ))}
        </div>
      ) : (
        <p className="text-sm text-snow-faint">{role === "racer" ? "Not set — their team's categories are used." : "All categories."}</p>
      )}
      {canSet && (
        <p className="text-xs text-snow-faint">
          {role === "racer" ? "Tap the classes they race in. None ticked: their team's categories are used." : "None ticked: all categories."}
        </p>
      )}
    </section>
  );
}
