"use client";

import { useState } from "react";
import type { Category } from "@/lib/categories";
import type { Weekend } from "@/lib/races";
import { CategoryChips } from "./CategoryTag";
import { WeekendCard } from "./WeekendCard";

/** Everyone's schedule: the category chips, then upcoming weekends and the past ones dimmed. */
export function ScheduleList({ upcoming, past, categories }: { upcoming: Weekend[]; past: Weekend[]; categories: Category[] }) {
  const [only, setOnly] = useState<string | null>(null);
  const fits = (w: Weekend) => !only || w.categoryIds.includes(only) || w.sessions.some((s) => s.categoryId === only);
  const current = [...upcoming, ...past][0]?.seasonId;
  const chips = categories.filter((c) => c.seasonId === current);
  const shown = [...upcoming, ...past].filter(fits);
  return (
    <div className="space-y-5">
      <CategoryChips categories={chips} value={only} onChange={setOnly} />
      {only && shown.length === 0 && <p className="card text-sm text-snow-faint">No weekend has this category yet.</p>}
      {/* Sessions start folded, as in the app: the arrow opens them. */}
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
        {shown.map((w) => (
          <WeekendCard key={w.id} weekend={w} isAdmin={false} href={`/w/${w.id}`} showSeason dimmed={past.includes(w)} categories={categories} only={only} />
        ))}
      </div>
    </div>
  );
}
