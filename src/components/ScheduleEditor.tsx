"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Category } from "@/lib/categories";
import type { Weekend } from "@/lib/races";
import type { Season } from "@/lib/seasons";
import { EmptyState } from "./EmptyState";
import { CategoriesEditor } from "./schedule/CategoriesEditor";
import { CategoryChips, type WeekendCounts } from "./schedule/CategoryTag";
import { WeekendCard } from "./schedule/WeekendCard";
import { WeekendForm } from "./schedule/WeekendForms";

/** The admin's schedule: weekends and their sessions, edited in place. Every time change goes out to everyone. */
export function ScheduleEditor({
  weekends,
  seasons,
  categories,
  counts,
}: {
  weekends: Weekend[];
  seasons: Season[];
  categories: Category[];
  /** How many weekends each category runs, for the chips. */
  counts?: WeekendCounts;
}) {
  const router = useRouter();
  const [creating, setCreating] = useState(false);
  const [editingCategories, setEditingCategories] = useState(false);
  const [only, setOnly] = useState<string | null>(null);
  const current = seasons.find((s) => s.current);
  const shownCategories = categories.filter((c) => c.seasonId === current?.id);
  const shown = only ? weekends.filter((w) => w.categoryIds.includes(only) || w.sessions.some((s) => s.categoryId === only)) : weekends;
  const onlyIds = only ? [only] : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="page-title">Schedule</h1>
        <div className="flex gap-2">
          <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setEditingCategories((v) => !v)}>
            Categories
          </button>
          <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => setCreating(true)}>
            New race weekend
          </button>
        </div>
      </div>
      {editingCategories && <CategoriesEditor seasons={seasons} categories={categories} onClose={() => setEditingCategories(false)} />}
      <CategoryChips categories={shownCategories} value={only} onChange={setOnly} counts={counts} />
      {creating && (
        <WeekendForm
          seasons={seasons}
          categories={categories}
          onDone={() => {
            setCreating(false);
            router.refresh();
          }}
          onCancel={() => setCreating(false)}
        />
      )}
      {weekends.length === 0 && !creating && (
        <EmptyState
          icon="calendar"
          title="No weekends yet"
          line="Add the season's first race weekend; everyone sees it here, with its sessions."
          action={{ label: "Add a race weekend", onClick: () => setCreating(true) }}
        />
      )}
      {only && shown.length === 0 && <p className="card text-sm text-snow-faint">No weekend has this category yet.</p>}
      {Array.from(new Set(shown.map((w) => w.seasonName ?? ""))).map((seasonName) => (
        <div key={seasonName || "none"} className="space-y-4">
          {seasonName && <h2 className="text-xs font-semibold uppercase tracking-wide text-snow-faint">{seasonName}</h2>}
          {shown
            .filter((w) => (w.seasonName ?? "") === seasonName)
            .map((w) => (
              <WeekendCard key={w.id} weekend={w} isAdmin seasons={seasons} href={`/w/${w.id}`} categories={categories} only={onlyIds} />
            ))}
        </div>
      ))}
    </div>
  );
}
