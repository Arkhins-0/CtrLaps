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

/** "All" and a chip per category: which category's sessions the schedule shows. */
export function CategoryChips({ categories, value, onChange }: { categories: Category[]; value: string | null; onChange: (id: string | null) => void }) {
  if (categories.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Show sessions of">
      <button className={`chip ${value === null ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`} onClick={() => onChange(null)}>
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
