import { body, handle, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { categoriesOf } from "@/lib/categories";
import { fail, json } from "@/lib/http";
import { currentSeason } from "@/lib/seasons";
import { createTeam, listTeams, setTeamEntries, teamByName } from "@/lib/teams";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

const ids = (v: unknown): string[] =>
  Array.isArray(v) ? v.filter((x): x is string => typeof x === "string" && /^[0-9a-f-]{36}$/i.test(x)).slice(0, 30) : [];

/** Every team with its entries in the current season (or `?season=`), and that season's categories. */
export const GET = handle(async (request) => {
  await requireUser(["admin", "coordinator", "team_manager"]);
  const seasonId = new URL(request.url).searchParams.get("season") || (await currentSeason()).id;
  return json({ teams: await listTeams(seasonId), categories: await categoriesOf([seasonId]), seasonId });
});

/** Admin or coordinator: a new team `{name, categoryIds}`, entered in the current season's categories given. */
export const POST = handle(async (request) => {
  const me = await requireUser(["admin", "coordinator"]);
  const b = await body(request);
  const name = str(b.name, 80);
  if (name.length < 2) return fail("Give the team a name.");
  if (await teamByName(name)) return fail("There is already a team with that name.", 409);
  const id = await createTeam(name);
  const season = (await currentSeason()).id;
  await setTeamEntries(id, season, ids(b.categoryIds));
  await audit(me.id, null, "team.created", { id, name });
  return json({ teams: await listTeams(season) }, 201);
});
