"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { Category } from "@/lib/categories";
import { contrastText } from "@/lib/colors";

/** A user (no role yet) picks the race categories they follow: their posts reach Home, their sessions are "Mine". */
export function FollowCategories({ categories, initial }: { categories: Category[]; initial: string[] }) {
  const router = useRouter();
  const [ids, setIds] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (categories.length === 0) return null;

  const toggle = async (id: string) => {
    if (busy) return;
    const before = ids;
    const next = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
    setIds(next);
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ categoryIds: string[] }>("/api/me/following", { method: "PUT", json: { categoryIds: next } });
      setIds(r.categoryIds);
      router.refresh();
    } catch (e) {
      setIds(before);
      setError(e instanceof Error ? e.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card space-y-3">
      <div>
        <h2 className="font-semibold">Follow categories</h2>
        <p className="text-xs text-snow-faint">Their channel posts come to your Home, and their sessions show under Mine on the schedule.</p>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="flex flex-wrap gap-2">
        {categories.map((c) => {
          const on = ids.includes(c.id);
          return (
            <button
              key={c.id}
              type="button"
              className="chip"
              style={on ? { backgroundColor: c.color, borderColor: c.color, color: contrastText(c.color) } : { borderColor: `${c.color}80`, color: c.color }}
              onClick={() => toggle(c.id)}
              title={c.name}
              aria-pressed={on}
            >
              {c.code}
            </button>
          );
        })}
      </div>
    </section>
  );
}
