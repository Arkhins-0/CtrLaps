import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { categoriesOf } from "@/lib/categories";
import { one } from "@/lib/db";
import { canEdit } from "@/lib/hierarchy";
import { fail, json } from "@/lib/http";
import { currentSeason } from "@/lib/seasons";
import { assignedCategories, setAssignedCategories, teamCategories } from "@/lib/teams";
import { audit, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * The race categories a racer races in, or a race official looks after, this season `{categoryIds}`. Their manager,
 * a coordinator or an admin sets them; a racer's are limited to their team's entries when the team has any.
 */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user) return fail("No such person.", 404);
  if (user.role !== "racer" && user.role !== "race_official") return fail("Only racers and race officials are given categories.");
  if (!(me.role === "admin" || me.role === "coordinator" || canEdit(me, user))) return fail("Only their manager, a coordinator or an admin can change this.", 403);

  const season = (await currentSeason()).id;
  const all = (await categoriesOf([season])).map((c) => c.id);
  const team = (await one<{ team_id: string | null }>("SELECT team_id FROM users WHERE id = $1", [id]))?.team_id ?? null;
  const entered = user.role === "racer" ? await teamCategories(team, season) : [];
  const allowed = entered.length > 0 ? entered : all;
  const b = await body(request);
  const wanted = Array.isArray(b.categoryIds) ? b.categoryIds.filter((x): x is string => typeof x === "string" && allowed.includes(x)) : [];
  await setAssignedCategories(id, season, wanted);
  await audit(me.id, id, "user.categories", { categoryIds: wanted });
  return json({ categoryIds: await assignedCategories(id, season) });
});
