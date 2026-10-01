import { handle, isUuid } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { standingsPage } from "@/lib/results";

export const dynamic = "force-dynamic";

/**
 * A season's standings (`?season=<id>`: the current one by default, archived ones too) for one race category
 * (`?category=<id>`; by default the first of the person's own, else the first): the seasons and categories to choose
 * from, drivers, teams, and the sessions that have results.
 */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const query = new URL(request.url).searchParams;
  const asked = query.get("category");
  const season = query.get("season");
  return json(await standingsPage(user, asked && isUuid(asked) ? asked : null, season && isUuid(season) ? season : null));
});
