import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";
import { moveVolunteer, volunteerGroupDetail } from "@/lib/volunteers";

export const dynamic = "force-dynamic";

/** Put a volunteer in this group `{userId}` (from no group, or moved by an admin or their current group's coordinator). */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser(["admin", "coordinator"]);
  const { id } = await params;
  const userId = str((await body(request)).userId, 64);
  if (!isUuid(id) || !isUuid(userId)) return fail("No such volunteer.", 404);
  await moveVolunteer(user, userId, id);
  await audit(user.id, userId, "volunteers.moved", { to: id });
  return json({ group: await volunteerGroupDetail(user, id).catch(() => null) });
});

/** Take a volunteer out of this group (into none) `?userId=`: an admin or this group's coordinator. */
export const DELETE = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser(["admin", "coordinator"]);
  const { id } = await params;
  const userId = new URL(request.url).searchParams.get("userId") ?? "";
  if (!isUuid(id) || !isUuid(userId)) return fail("No such volunteer.", 404);
  await moveVolunteer(user, userId, null);
  await audit(user.id, userId, "volunteers.moved", { from: id, to: null });
  return json({ group: await volunteerGroupDetail(user, id) });
});
