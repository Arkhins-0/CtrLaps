import { EmptyState } from "@/components/EmptyState";
import { ScheduleEditor } from "@/components/ScheduleEditor";
import { SeasonBar } from "@/components/SeasonBar";
import type { WeekendCounts } from "@/components/schedule/CategoryTag";
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
  // The numbers on the filter chips: every weekend for All, and for a category the weekends its filter shows: those
  // that list it or have a session in it, and, on everyone's list (not the admins' editor), those listing none.
  const counts: WeekendCounts = {
    all: weekends.length,
    byCategory: Object.fromEntries(
      categories.map((c) => [
        c.id,
        weekends.filter((w) => (!isAdmin && w.categoryIds.length === 0) || w.categoryIds.includes(c.id) || w.sessions.some((s) => s.categoryId === c.id)).length,
      ]),
    ),
  };

  return (
    <div className="space-y-6">
      <SeasonBar seasons={seasons} isAdmin={isAdmin} />
      {isAdmin ? (
        <ScheduleEditor weekends={weekends} seasons={seasons.filter((s) => s.status === "active")} categories={categories} counts={counts} />
      ) : (
        <>
          <h1 className="page-title">Schedule</h1>
          {weekends.length === 0 && (
            <EmptyState icon="calendar" title="No weekends yet" line="Race weekends and their sessions show here once the organisers add them." />
          )}
          <ScheduleList upcoming={upcoming} past={past} categories={categories} counts={counts} mine={await myCategories(user)} editTimes={user.role === "coordinator"} />
        </>
      )}
    </div>
  );
}
