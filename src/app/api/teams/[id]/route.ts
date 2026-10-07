import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { one } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { currentSeason } from "@/lib/seasons";
import { canEditTeam, deleteTeam, listTeams, renameTeam, setTeamEntries, teamByName, teamPage } from "@/lib/teams";
import { descendants } from "@/lib/hierarchy";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * A team's page, for anyone signed in. `canEdit`: may change its photo. `canOpen`: the people whose own page the viewer
 * may open (those below them, as on People).
 */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such team.", 404);
  const [page, below, canEdit] = await Promise.all([teamPage(id), descendants(me), canEditTeam(me, id)]);
  if (!page) return fail("No such team.", 404);
  const ids = new Set(below.map((u) => u.id));
  return json({ ...page, canEdit, canOpen: page.people.filter((p) => ids.has(p.id) || p.id === me.id).map((p) => p.id) });
});

/** Admin or coordinator: rename `{name}` and/or set the current season's entries `{categoryIds}`. */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser(["admin", "coordinator"]);
  const { id } = await params;
  if (!isUuid(id) || !(await one("SELECT 1 FROM teams WHERE id = $1", [id]))) return fail("No such team.", 404);
  const b = await body(request);
  const season = (await currentSeason()).id;
  if (typeof b.name === "string") {
    const name = str(b.name, 80);
    if (name.length < 2) return fail("Give the team a name.");
    const other = await teamByName(name);
    if (other && other.id !== id) return fail("There is already a team with that name.", 409);
    await renameTeam(id, name);
  }
  if (Array.isArray(b.categoryIds)) {
    const ids = b.categoryIds.filter((x): x is string => typeof x === "string" && isUuid(x)).slice(0, 30);
    await setTeamEntries(id, season, ids);
  }
  await audit(me.id, null, "team.updated", { id, name: b.name, categoryIds: b.categoryIds });
  return json({ teams: await listTeams(season) });
});

/** Admin or coordinator: delete a team; its people keep no team. */
export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser(["admin", "coordinator"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such team.", 404);
  await deleteTeam(id);
  await audit(me.id, null, "team.deleted", { id });
  return json({ teams: await listTeams((await currentSeason()).id) });
});
