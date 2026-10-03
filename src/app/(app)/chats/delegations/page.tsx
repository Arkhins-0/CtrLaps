import { notFound } from "next/navigation";
import { VolunteerGroups } from "@/components/volunteers/VolunteerGroups";
import { requireProfile } from "@/lib/session";
import { listVolunteerGroups, syncAllVolunteerGroups } from "@/lib/volunteers";

export const metadata = { title: "Delegations" };

/** The delegations, for admins and coordinators. A delegate's own delegation is pinned on their Chats page. */
export default async function Delegations() {
  const user = await requireProfile();
  if (user.role !== "admin" && user.role !== "coordinator") notFound();
  await syncAllVolunteerGroups();
  const [groups, coordinators] = await Promise.all([
    listVolunteerGroups(user, "delegation"),
    Promise.resolve([] as { id: string; name: string }[]),
  ]);
  return <VolunteerGroups kind="delegation" groups={groups} isAdmin={user.role === "admin"} coordinators={coordinators} />;
}
