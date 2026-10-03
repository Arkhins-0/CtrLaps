import { handle } from "@/lib/api";
import { activity, isActivityKind, isActivityPeriod, isActivityRole } from "@/lib/activity";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";

export const dynamic = "force-dynamic";

/**
 * The Activity log, developers only. `?kind=messages|schedule|results|people`, `?q=` words to find, `?role=` who did it
 * (a role, `developer` or `system`), `?period=today|7d|30d`, `?sort=oldest` (newest first otherwise), `?before=<id>` for
 * the next page.
 */
export const GET = handle(async (request) => {
  const me = await requireUser();
  if (!isDeveloper(me)) return fail("Only the support team sees the activity log.", 403);
  const query = new URL(request.url).searchParams;
  const kind = query.get("kind");
  const role = query.get("role");
  const period = query.get("period");
  return json(
    await activity({
      before: query.get("before"),
      kind: isActivityKind(kind) ? kind : null,
      search: query.get("q"),
      role: isActivityRole(role) ? role : null,
      period: isActivityPeriod(period) ? period : null,
      oldest: query.get("sort") === "oldest",
    }),
  );
});
