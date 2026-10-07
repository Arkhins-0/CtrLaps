import { EmptyState } from "@/components/EmptyState";
import { ScheduleEditor } from "@/components/ScheduleEditor";
import { SeasonBar } from "@/components/SeasonBar";
import { ScheduleList } from "@/components/schedule/ScheduleList";
import { categoriesOf } from "@/lib/categories";
import { listWeekends } from "@/lib/races";
import { currentSeason, listSeasons } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";
import { myCategories } from "@/lib/teams";

export const metadata = { title: "Schedule" };

export default async function Schedule() {
  const user = await requireProfile();
  const [weekends, seasons] = await Promise.all([listWeekends(), listSeasons()]);
  const now = Date.now();
  const upcoming = weekends.filter((w) => new Date(`${w.endsOn}T23:59:59`).getTime() >= now - 86_400_000).reverse();
  const past = weekends.filter((w) => !upcoming.includes(w));
  const isAdmin = user.role === "admin";
  const categories = await categoriesOf([...weekends.map((w) => w.seasonId ?? ""), ...seasons.filter((s) => s.status === "active").map((s) => s.id), (await currentSeason()).id]);

  return (
    <div className="space-y-6">
      <SeasonBar seasons={seasons} isAdmin={isAdmin} />
      {isAdmin ? (
        <ScheduleEditor weekends={weekends} seasons={seasons.filter((s) => s.status === "active")} categories={categories} />
      ) : (
        <>
          <h1 className="page-title">Schedule</h1>
          {weekends.length === 0 && (
            <EmptyState icon="calendar" title="No weekends yet" line="Race weekends and their sessions show here once the organisers add them." />
          )}
          <ScheduleList upcoming={upcoming} past={past} categories={categories} mine={await myCategories(user)} editTimes={user.role === "coordinator"} />
        </>
      )}
    </div>
  );
}
