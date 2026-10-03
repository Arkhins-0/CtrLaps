import { body, handle, isUuid, str, uuids, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";
import { moveVolunteers, volunteerGroupDetail } from "@/lib/volunteers";

export const dynamic = "force-dynamic";

/** Move several of this group's volunteers at once `{userIds, toGroupId | null}`: an admin or this group's coordinator. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser(["admin", "coordinator"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such volunteer group.", 404);
  const b = await body(request);
  const to = str(b.toGroupId, 64) || null;
  if (to && !isUuid(to)) return fail("No such volunteer group.", 404);
  const moved = await moveVolunteers(user, id, uuids(b.userIds), to);
  await audit(user.id, null, "volunteers.moved", { from: id, to, count: moved });
  return json({ moved, group: await volunteerGroupDetail(user, id) });
});
