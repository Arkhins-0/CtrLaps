import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { one } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { setCategoryScoring } from "@/lib/results";
import { scoringInput } from "@/lib/scoring";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Admin: a category's points table `{scoring: {points, dnf, dns, dsq, pole, fastestLap}}`, or `{scoring: null}` for
 * none (points typed by hand). Results not typed by hand are scored again from it.
 */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id) || !(await one("SELECT 1 FROM categories WHERE id = $1", [id]))) return fail("No such category.", 404);
  const scoring = scoringInput((await body(request)).scoring);
  if (scoring && "error" in scoring) return fail(scoring.error);
  await setCategoryScoring(id, scoring);
  await audit(admin.id, null, "category.scoring_saved", { categoryId: id, scoring });
  return json({ scoring });
});
