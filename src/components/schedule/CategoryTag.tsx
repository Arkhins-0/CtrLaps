import type { Category } from "@/lib/categories";
import { contrastText } from "@/lib/colors";

/** A race category as a small tag in its colour: "ITC". */
export function CategoryTag({ category, title }: { category: Pick<Category, "code" | "color" | "name">; title?: boolean }) {
  return (
    <span
      className="inline-flex items-center rounded-md border px-1.5 py-px align-middle text-[10px] font-semibold tracking-wide"
      style={{ color: category.color, borderColor: `${category.color}66`, backgroundColor: `${category.color}1F` }}
      title={title === false ? undefined : category.name}
    >
      {category.code}
    </span>
  );
}

/** What the schedule shows: everything (null), "mine" (the person's own categories), or one category's id. */
export type CategoryFilter = string | null;

/** The categories a filter keeps, or null for everything. */
export const filterIds = (value: CategoryFilter, mine: string[] | null | undefined): string[] | null =>
  value === null ? null : value === "mine" ? (mine ?? null) : [value];

/** How many weekends a category runs, for the number on its chip: `all` is every weekend, for the All chip. */
export type WeekendCounts = { all: number; byCategory: Record<string, number> };

/** "Mine" (when the person has categories), "All" and a chip per category, each with how many weekends it holds. */
export function CategoryChips({
  categories,
  value,
  onChange,
  mine,
  counts,
}: {
  categories: Category[];
  value: CategoryFilter;
  onChange: (value: CategoryFilter) => void;
  mine?: string[] | null;
  counts?: WeekendCounts;
}) {
  if (categories.length === 0) return null;
  const chip = (on: boolean) => `chip ${on ? "border-gold bg-gold text-ink" : "hover:border-snow/40"}`;
  // The number, quieter than the name beside it.
  const count = (n: number | undefined) => counts && <span className="opacity-70">{n ?? 0}</span>;
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Show sessions of">
      {mine && mine.length > 0 && (
        <button className={chip(value === "mine")} onClick={() => onChange("mine")} title={categories.filter((c) => mine.includes(c.id)).map((c) => c.code).join(", ")}>
          Mine
        </button>
      )}
      <button className={chip(value === null)} onClick={() => onChange(null)}>
        All
        {count(counts?.all)}
      </button>
      {categories.map((c) => {
        const on = value === c.id;
        return (
          <button
            key={c.id}
            className="chip"
            style={on ? { backgroundColor: c.color, borderColor: c.color, color: contrastText(c.color) } : { borderColor: `${c.color}80`, color: c.color }}
            onClick={() => onChange(on ? null : c.id)}
            title={c.name}
          >
            {c.code}
            {count(counts?.byCategory[c.id])}
          </button>
        );
      })}
    </div>
  );
}
