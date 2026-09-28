import Link from "next/link";
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
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h1 className="page-title">Teams</h1>
          <p className="text-sm text-snow-faint">Tap a category to enter a team in it for {season.name}, or to take it out.</p>
        </div>
        <Link href="/people" className="btn-ghost px-3 py-1.5 text-xs">
          People
        </Link>
      </div>
      <TeamsEditor initial={teams} categories={categories} />
    </div>
  );
}
