import Link from "next/link";
import { notFound } from "next/navigation";
import { LocalTime } from "@/components/LocalTime";
import { CategoryTag } from "@/components/schedule/CategoryTag";
import { ResultsEditor } from "@/components/schedule/ResultsEditor";
import { categoryInfo } from "@/lib/categoryChannels";
import { canEnterResults, resultSession, sessionResults, teamsForResults } from "@/lib/results";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Results" };

/** One session's results. Admins, coordinators and the category's race officials enter them here. */
export default async function Results({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const session = await resultSession(id);
  if (!session) notFound();
  const [category, results, canEdit] = await Promise.all([
    session.categoryId ? categoryInfo(session.categoryId) : Promise.resolve(null),
    sessionResults(id),
    canEnterResults(user, session.categoryId),
  ]);
  const teams = canEdit && session.categoryId ? await teamsForResults(session.categoryId) : [];

  return (
    <div className="space-y-5">
      <div className="card flex items-center gap-3">
        {category && <CategoryTag category={category} />}
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-semibold">{session.name}</h1>
          <p className="text-xs text-snow-faint">
            <Link href={`/w/${session.weekendId}`} className="hover:text-snow">
              {session.weekendName}
            </Link>{" "}
            · <LocalTime iso={session.startsAt} />
          </p>
        </div>
        {category && (
          <Link href={`/standings?category=${category.id}`} className="btn-ghost shrink-0 px-3 py-1.5 text-xs">
            Standings
          </Link>
        )}
      </div>
      {!category && <p className="card text-sm text-snow-faint">This session has no category, so it has no results.</p>}
      {category && <ResultsEditor sessionId={id} initial={results} canEdit={canEdit} teams={teams} />}
    </div>
  );
}
