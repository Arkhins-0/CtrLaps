import { body, bool, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";
import { audit, toPublic, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * A developer makes another admin a developer `{dev: true}`, or takes it back `{dev: false}`. Your own flag is left to
 * the script (scripts/make-dev.mjs), so the last developer can't switch support off by a slip.
 */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  if (!isDeveloper(me)) return fail("Only a developer can do this.", 403);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user) return fail("No such person.", 404);
  if (user.id === me.id) return fail("You can't change your own developer access here.", 403);
  const dev = bool((await body(request)).dev);
  if (dev && (user.role !== "admin" || user.status !== "active")) return fail("Only an active admin can be made a developer.");
  await run("UPDATE users SET is_dev = $2 WHERE id = $1", [user.id, dev]);
  await audit(me.id, user.id, dev ? "developer.made" : "developer.removed");
  return json({ user: toPublic((await userById(user.id))!) });
});
