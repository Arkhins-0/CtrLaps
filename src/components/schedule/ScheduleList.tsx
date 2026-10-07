"use client";

import { useState } from "react";
import type { Category } from "@/lib/categories";
import type { Weekend } from "@/lib/races";
import { CategoryChips, filterIds, type CategoryFilter, type WeekendCounts } from "./CategoryTag";
import { WeekendCard } from "./WeekendCard";

/** Everyone's schedule: the category chips, then upcoming weekends and the past ones dimmed. */
export function ScheduleList({
  upcoming,
  past,
  categories,
  counts,
  mine,
  editTimes = false,
}: {
  upcoming: Weekend[];
  past: Weekend[];
  categories: Category[];
  /** How many weekends each category runs, for the chips. */
  counts?: WeekendCounts;
  /** The person's own categories ("Mine", the default when they have any); null = none. */
  mine: string[] | null;
  /** A coordinator: may change sessions' times. */
  editTimes?: boolean;
}) {
  const [filter, setFilter] = useState<CategoryFilter>(mine ? "mine" : null);
  const only = filterIds(filter, mine);
  // A weekend that lists no categories is for everyone.
  const fits = (w: Weekend) =>
    !only || w.categoryIds.length === 0 || w.categoryIds.some((id) => only.includes(id)) || w.sessions.some((s) => s.categoryId && only.includes(s.categoryId));
  const current = [...upcoming, ...past][0]?.seasonId;
  const chips = categories.filter((c) => c.seasonId === current);
  const shown = [...upcoming, ...past].filter(fits);
  return (
    <div className="space-y-5">
      <CategoryChips categories={chips} value={filter} onChange={setFilter} mine={mine} counts={counts} />
      {only && shown.length === 0 && <p className="card text-sm text-snow-faint">No weekend has this category yet.</p>}
      {/* Sessions start folded, as in the app: the arrow opens them. */}
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
        {shown.map((w) => (
          <WeekendCard key={w.id} weekend={w} isAdmin={false} editTimes={editTimes} href={`/w/${w.id}`} showSeason dimmed={past.includes(w)} categories={categories} only={only} />
        ))}
      </div>
    </div>
  );
}
