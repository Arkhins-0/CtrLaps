import { body, handle, isUuid, type Params } from "@/lib/api";
import { AuthError, requireUser } from "@/lib/auth";
import { categoryInfo } from "@/lib/categoryChannels";
import { fail, json } from "@/lib/http";
import { canEnterResults, resultInput, resultSession, saveResults, sessionResults, teamsForResults } from "@/lib/results";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** A session's results, for everyone signed in; with the teams to pick from for those who may enter them. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such session.", 404);
  const session = await resultSession(id);
  if (!session) return fail("No such session.", 404);
  const [category, results, canEdit] = await Promise.all([
    session.categoryId ? categoryInfo(session.categoryId) : Promise.resolve(null),
    sessionResults(id),
    canEnterResults(user, session.categoryId),
  ]);
  return json({
    session,
    category: category && { id: category.id, name: category.name, code: category.code, color: category.color },
    results,
    canEdit,
    teams: canEdit && session.categoryId ? await teamsForResults(session.categoryId) : [],
  });
});

/** Replace the results `{results: [{position, status, carNumber, driverName, teamId, points, bestLap}]}`. */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such session.", 404);
  const session = await resultSession(id);
  if (!session) return fail("No such session.", 404);
  if (!(await canEnterResults(user, session.categoryId))) throw new AuthError(403, "Only admins, coordinators and this category's race officials enter results.");
  const raw = (await body(request)).results;
  if (!Array.isArray(raw)) return fail("Send the results as a list.");
  await saveResults(session, raw.map(resultInput));
  await audit(user.id, null, "session.results_saved", { sessionId: id, rows: raw.length });
  return json({ results: await sessionResults(id) });
});
