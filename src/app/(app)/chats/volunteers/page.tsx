import { notFound } from "next/navigation";
import { VolunteerGroups } from "@/components/volunteers/VolunteerGroups";
import { q } from "@/lib/db";
import { requireProfile } from "@/lib/session";
import { listVolunteerGroups, syncAllVolunteerGroups } from "@/lib/volunteers";

export const metadata = { title: "Volunteers" };

/** The volunteer groups, for admins (all) and coordinators (those they lead). A volunteer's own group is pinned on their Chats page. */
export default async function Volunteers() {
  const user = await requireProfile();
  if (user.role !== "admin" && user.role !== "coordinator") notFound();
  await syncAllVolunteerGroups();
  const [groups, coordinators] = await Promise.all([
    listVolunteerGroups(user, "volunteer"),
    user.role === "admin"
      ? q<{ id: string; name: string }>("SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY 2")
      : Promise.resolve([]),
  ]);
  return <VolunteerGroups kind="volunteer" groups={groups} isAdmin={user.role === "admin"} coordinators={coordinators} />;
}
