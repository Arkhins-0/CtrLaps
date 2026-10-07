import { PageHeader } from "@/components/AppUI";
import { notFound } from "next/navigation";
import { TeamsEditor } from "@/components/TeamsEditor";
import { categoriesOf } from "@/lib/categories";
import { currentSeason } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";
import { listTeams } from "@/lib/teams";

export const metadata = { title: "Teams" };

/** Admins and coordinators: the teams, and which race categories each runs this season. */
export default async function Teams() {
  const user = await requireProfile();
  if (user.role !== "admin" && user.role !== "coordinator") notFound();
  const season = await currentSeason();
  const [teams, categories] = await Promise.all([listTeams(season.id), categoriesOf([season.id])]);
  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <PageHeader title="Teams" icon="people" back="/people" sub={`Tap a category to enter a team in it for ${season.name}, or to take it out.`} />
      <TeamsEditor initial={teams} categories={categories} />
    </div>
  );
}
