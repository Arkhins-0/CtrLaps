import "server-only";

import { q, tx } from "./db";
import { isColor } from "./categoryColors";

/*
 * Race categories (classes) per season — Formula LGB 1300, ITC… A weekend lists the ones racing that round; a
 * session may belong to one. See wink-docs/13-race-categories-plan.md.
 */

export type Category = { id: string; seasonId: string; name: string; code: string; color: string; position: number };

type Row = { id: string; season_id: string; name: string; code: string; color: string; position: number };

const category = (r: Row): Category => ({ id: r.id, seasonId: r.season_id, name: r.name, code: r.code, color: r.color, position: r.position });

/** The categories of these seasons, in their order. */
export async function categoriesOf(seasonIds: string[]): Promise<Category[]> {
  const ids = Array.from(new Set(seasonIds.filter(Boolean)));
  if (ids.length === 0) return [];
  const rows = await q<Row>(
    "SELECT id, season_id, name, code, color, position FROM categories WHERE season_id = ANY($1::uuid[]) ORDER BY position, name",
    [ids],
  );
  return rows.map(category);
}

export type CategoryInput = { id?: string | null; name: string; code: string; color: string };

/** The admin's list, checked: names, short codes (unique in the season) and colours. */
export function categoriesInput(value: unknown): CategoryInput[] | { error: string } {
  if (!Array.isArray(value)) return { error: "Send the list of categories." };
  if (value.length > 30) return { error: "Up to 30 categories." };
  const out: CategoryInput[] = [];
  const codes = new Set<string>();
  for (const item of value) {
    const v = (item ?? {}) as Record<string, unknown>;
    const name = String(v.name ?? "").trim().slice(0, 60);
    const code = String(v.code ?? "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
    const color = String(v.color ?? "").trim();
    const id = typeof v.id === "string" && /^[0-9a-f-]{36}$/i.test(v.id) ? v.id : null;
    if (name.length < 2) return { error: "Give every category a name." };
    if (code.length < 1) return { error: `Give ${name} a short code (letters and digits, like ITC).` };
    if (codes.has(code)) return { error: `Two categories share the code ${code}.` };
    if (!isColor(color)) return { error: `Pick a colour for ${name}.` };
    codes.add(code);
    out.push({ id, name, code, color });
  }
  return out;
}

/**
 * Replace a season's categories with this list, in this order. Kept ones keep their id (and so their sessions and
 * weekends); ones left out are removed — their sessions become for everyone.
 */
export async function saveCategories(seasonId: string, list: CategoryInput[]): Promise<void> {
  await tx(async (c) => {
    const existing = (await c.query<{ id: string }>("SELECT id FROM categories WHERE season_id = $1", [seasonId])).rows.map((r) => r.id);
    const kept = list.filter((x) => x.id && existing.includes(x.id)).map((x) => x.id as string);
    await c.query("DELETE FROM categories WHERE season_id = $1 AND NOT (id = ANY($2::uuid[]))", [seasonId, kept]);
    // Codes are unique per season: move the kept ones out of the way first, so two can swap codes.
    await c.query("UPDATE categories SET code = '~' || id::text WHERE season_id = $1", [seasonId]);
    for (const [position, x] of list.entries()) {
      if (x.id && kept.includes(x.id)) {
        await c.query("UPDATE categories SET name = $2, code = $3, color = $4, position = $5 WHERE id = $1", [x.id, x.name, x.code, x.color, position]);
      } else {
        await c.query("INSERT INTO categories (season_id, name, code, color, position) VALUES ($1, $2, $3, $4, $5)", [
          seasonId,
          x.name,
          x.code,
          x.color,
          position,
        ]);
      }
    }
  });
}
