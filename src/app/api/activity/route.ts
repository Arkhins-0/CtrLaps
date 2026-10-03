import { handle } from "@/lib/api";
import { activity, isActivityKind } from "@/lib/activity";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";

export const dynamic = "force-dynamic";

/** The Activity log, developers only: `?kind=messages|schedule|results|people`, `?before=<id>` for the next page. */
export const GET = handle(async (request) => {
  const me = await requireUser();
  if (!isDeveloper(me)) return fail("Only the support team sees the activity log.", 403);
  const query = new URL(request.url).searchParams;
  const kind = query.get("kind");
  return json(await activity({ before: query.get("before"), kind: isActivityKind(kind) ? kind : null }));
});
