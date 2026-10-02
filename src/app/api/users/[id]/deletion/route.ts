import { handle, isUuid, type Params } from "@/lib/api";
import { cancelDeletion, deletionDue, requestDeletion } from "@/lib/accountDeletion";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";
import { userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/** A developer, on someone's request (by email or a ticket), starts deleting their account: the same 7-day wait. */
export const POST = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  if (!isDeveloper(me)) return fail("Only a developer can delete an account.", 403);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user || user.status === "deleted") return fail("No such person.", 404);
  if (user.id === me.id) return fail("Delete your own account from your Account page.", 403);
  return json(await requestDeletion(user, me));
});

/** A developer withdraws a deletion that is still waiting. */
export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  if (!isDeveloper(me)) return fail("Only a developer can do this.", 403);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  if (!(await deletionDue(id))) return fail("This account isn't waiting to be deleted.");
  await cancelDeletion(id, me.id);
  return json({ ok: true });
});
