import { handle, isUuid } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { standingsPage } from "@/lib/results";

export const dynamic = "force-dynamic";

/**
 * This season's standings for one race category (`?category=<id>`; by default the first of the person's own, else
 * the first): the categories to choose from, drivers, teams, and the sessions that have results.
 */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const asked = new URL(request.url).searchParams.get("category");
  return json(await standingsPage(user, asked && isUuid(asked) ? asked : null));
});
