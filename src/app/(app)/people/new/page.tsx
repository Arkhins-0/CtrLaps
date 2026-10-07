import { notFound } from "next/navigation";
import { NewPersonForm } from "@/components/NewPersonForm";
import { CREATE_RULES } from "@/lib/roles";
import { q } from "@/lib/db";
import { requireProfile } from "@/lib/session";
import { listVolunteerGroups } from "@/lib/volunteers";

export const metadata = { title: "Add or promote" };

export default async function NewPerson() {
  const user = await requireProfile();
  const roles = CREATE_RULES[user.role] ?? [];
  const teamNames = (await q<{ name: string }>("SELECT name FROM teams ORDER BY lower(name)")).map((t) => t.name);
  if (roles.length === 0) notFound();
  // Delegates can be put in a delegation as they are added.
  const delegations = roles.includes("race_official") ? (await listVolunteerGroups(user, "delegation")).map((g) => ({ id: g.id, name: g.name })) : [];
  return (
    <div className="space-y-4">
      <h1 className="page-title">Add or promote</h1>
      <NewPersonForm roles={roles} teamName={user.team_name} creatorRole={user.role} teamNames={teamNames} delegations={delegations} />
    </div>
  );
}
