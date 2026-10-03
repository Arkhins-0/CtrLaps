import { body, handle, isUuid, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";
import { q } from "@/lib/db";
import { createVolunteerGroup, listVolunteerGroups, syncAllVolunteerGroups } from "@/lib/volunteers";

export const dynamic = "force-dynamic";

/** The volunteer groups I see: an admin all; a coordinator those they lead; a volunteer their own. For an admin, who can lead one. */
export const GET = handle(async () => {
  const user = await requireUser();
  await syncAllVolunteerGroups();
  const coordinators =
    user.role === "admin"
      ? await q<{ id: string; name: string }>("SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY 2")
      : [];
  return json({ groups: await listVolunteerGroups(user), canCreate: user.role === "admin" || user.role === "coordinator", coordinators });
});

/** A new group `{name, coordinatorId?}`: a coordinator leads what they make; an admin names the coordinator. */
export const POST = handle(async (request) => {
  const user = await requireUser(["admin", "coordinator"]);
  const b = await body(request);
  const coordinatorId = str(b.coordinatorId, 64) || null;
  if (coordinatorId && !isUuid(coordinatorId)) return fail("No such coordinator.");
  const id = await createVolunteerGroup(user, str(b.name, 80), coordinatorId);
  await audit(user.id, null, "volunteers.group_created", { groupId: id, name: str(b.name, 80) });
  return json({ id }, 201);
});
