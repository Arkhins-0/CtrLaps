import type { Category } from "@/lib/categories";

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

/** "Mine" (when the person has categories), "All" and a chip per category. */
export function CategoryChips({
  categories,
  value,
  onChange,
  mine,
}: {
  categories: Category[];
  value: CategoryFilter;
  onChange: (value: CategoryFilter) => void;
  mine?: string[] | null;
}) {
  if (categories.length === 0) return null;
  const chip = (on: boolean) => `chip ${on ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`;
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Show sessions of">
      {mine && mine.length > 0 && (
        <button className={chip(value === "mine")} onClick={() => onChange("mine")} title={categories.filter((c) => mine.includes(c.id)).map((c) => c.code).join(", ")}>
          Mine
        </button>
      )}
      <button className={chip(value === null)} onClick={() => onChange(null)}>
        All
      </button>
      {categories.map((c) => {
        const on = value === c.id;
        return (
          <button
            key={c.id}
            className="chip"
            style={on ? { backgroundColor: c.color, borderColor: c.color, color: "#0B0B0C" } : { borderColor: `${c.color}80`, color: c.color }}
            onClick={() => onChange(on ? null : c.id)}
            title={c.name}
          >
            {c.code}
          </button>
        );
      })}
    </div>
  );
}
