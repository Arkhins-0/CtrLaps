import { body, handle, isUuid, type Params } from "@/lib/api";
import { AuthError, requireUser } from "@/lib/auth";
import { categoryInfo } from "@/lib/categoryChannels";
import { fail, json } from "@/lib/http";
import { after } from "next/server";
import { canEnterResults, categoryScoring, entrants, notifyResults, resultInput, resultSession, saveResults, sessionResults, teamsForResults } from "@/lib/results";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** A session's results, for everyone signed in; with the teams to pick from for those who may enter them. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such session.", 404);
  const session = await resultSession(id);
  if (!session) return fail("No such session.", 404);
  const [category, results, canEdit, scoring] = await Promise.all([
    session.categoryId ? categoryInfo(session.categoryId) : Promise.resolve(null),
    sessionResults(id),
    canEnterResults(user, session.categoryId),
    categoryScoring(session.categoryId),
  ]);
  return json({
    // The category's points table (null: points are typed), and whether this session scores from it.
    scoring,
    scores: session.scores,
    session,
    category: category && { id: category.id, name: category.name, code: category.code, color: category.color },
    results,
    canEdit,
    // For those who may enter results: every team (older apps), and the teams with their racers to pick from.
    teams: canEdit && session.categoryId ? await teamsForResults(session.categoryId) : [],
    entrants: canEdit && session.categoryId ? await entrants(session.categoryId) : [],
  });
});

/**
 * Replace the results `{results: [{position, status, carNumber, driverName, userId, teamId, points, bestLap, pole,
 * fastestLap, manualPoints}], scores}` (`userId`: the racer picked; none for a name typed in): a row's points come from the category's table unless `manualPoints`; `scores` says
 * whether this session scores from the table at all.
 */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such session.", 404);
  const session = await resultSession(id);
  if (!session) return fail("No such session.", 404);
  if (!(await canEnterResults(user, session.categoryId))) throw new AuthError(403, "Only admins and coordinators enter results.");
  const b = await body(request);
  const raw = b.results;
  if (!Array.isArray(raw)) return fail("Send the results as a list.");
  await saveResults(session, raw.map(resultInput), typeof b.scores === "boolean" ? b.scores : undefined);
  // "Notify": a push to everyone following the category, and the results email to those who keep it on.
  if (b.notify === true) after(() => notifyResults(id));
  await audit(user.id, null, "session.results_saved", { sessionId: id, rows: raw.length });
  return json({ results: await sessionResults(id) });
});
