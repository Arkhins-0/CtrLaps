import { body, bool, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";
import { updateVolunteerGroup, volunteerGroupDetail } from "@/lib/volunteers";

export const dynamic = "force-dynamic";

/** One group, for its coordinator or an admin: its volunteers and what they may do, and where they can be moved. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such volunteer group.", 404);
  return json({ group: await volunteerGroupDetail(user, id) });
});

/** Its coordinator or an admin: `{name?, coordinatorId?, open?}` — rename, hand to another coordinator, open or close the chat. */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such volunteer group.", 404);
  const b = await body(request);
  const changes = {
    name: typeof b.name === "string" ? str(b.name, 80) : undefined,
    coordinatorId: typeof b.coordinatorId === "string" ? str(b.coordinatorId, 64) : undefined,
    open: typeof b.open === "boolean" ? bool(b.open) : undefined,
  };
  if (changes.coordinatorId && !isUuid(changes.coordinatorId)) return fail("No such coordinator.");
  await updateVolunteerGroup(user, id, changes);
  await audit(user.id, null, "volunteers.group_updated", { groupId: id, ...changes });
  return json({ group: await volunteerGroupDetail(user, id).catch(() => null) });
});
