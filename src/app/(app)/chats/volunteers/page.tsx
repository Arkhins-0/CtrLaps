import { notFound } from "next/navigation";
import { VolunteerGroups } from "@/components/volunteers/VolunteerGroups";
import { q } from "@/lib/db";
import { requireProfile } from "@/lib/session";
import { listVolunteerGroups, syncAllVolunteerGroups } from "@/lib/volunteers";

export const metadata = { title: "Volunteers" };

/** The volunteer groups' chats: admins see all, a coordinator the groups they lead, a volunteer their own. */
export default async function Volunteers() {
  const user = await requireProfile();
  if (!["admin", "coordinator", "volunteer"].includes(user.role)) notFound();
  await syncAllVolunteerGroups();
  const [groups, coordinators] = await Promise.all([
    listVolunteerGroups(user),
    user.role === "admin"
      ? q<{ id: string; name: string }>("SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY 2")
      : Promise.resolve([]),
  ]);
  return <VolunteerGroups groups={groups} canCreate={user.role === "admin" || user.role === "coordinator"} isAdmin={user.role === "admin"} coordinators={coordinators} />;
}
