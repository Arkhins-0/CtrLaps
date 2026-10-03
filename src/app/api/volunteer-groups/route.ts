import { body, handle, isUuid, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { q } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";
import { createVolunteerGroup, isGroupKind, listVolunteerGroups, syncAllVolunteerGroups } from "@/lib/volunteers";

export const dynamic = "force-dynamic";

/**
 * The groups I see, of one kind (`?kind=delegation`; volunteer groups otherwise). Volunteer groups: an admin all, a
 * coordinator those they lead, a volunteer their own. Delegations: admins and coordinators all, a delegate their own.
 * For an admin making a volunteer group: who can lead one.
 */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const asked = new URL(request.url).searchParams.get("kind");
  const kind = isGroupKind(asked) ? asked : "volunteer";
  await syncAllVolunteerGroups();
  const coordinators =
    user.role === "admin" && kind === "volunteer"
      ? await q<{ id: string; name: string }>("SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY 2")
      : [];
  return json({ kind, groups: await listVolunteerGroups(user, kind), canCreate: user.role === "admin" || user.role === "coordinator", coordinators });
});

/** A new group `{name, kind?, coordinatorId?}`: a coordinator leads the volunteer group they make, an admin names its coordinator; a delegation has no lead. */
export const POST = handle(async (request) => {
  const user = await requireUser(["admin", "coordinator"]);
  const b = await body(request);
  const kind = isGroupKind(b.kind) ? b.kind : "volunteer";
  const coordinatorId = str(b.coordinatorId, 64) || null;
  if (coordinatorId && !isUuid(coordinatorId)) return fail("No such coordinator.");
  const id = await createVolunteerGroup(user, str(b.name, 80), coordinatorId, kind);
  await audit(user.id, null, "volunteers.group_created", { groupId: id, kind, name: str(b.name, 80) });
  return json({ id }, 201);
});
