import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { q } from "@/lib/db";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** About → Developers / Helpers: the people behind the app, set only in the database (scripts/credits.mjs). */
export const GET = handle(async () => {
  await requireUser();
  const rows = await q<{ id: string; name: string; subtext: string; link: string; photo_url: string }>(
    "SELECT id, name, subtext, link, photo_url FROM app_credits ORDER BY position, created_at",
  );
  return json({ credits: rows.map((r) => ({ id: r.id, name: r.name, subtext: r.subtext, link: r.link || null, photoUrl: r.photo_url || null })) });
});
