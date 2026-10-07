import { notFound } from "next/navigation";
import Link from "next/link";
import { GroupTitle, ProfileBanner } from "@/components/AppUI";
import { Avatar } from "@/components/Avatar";
import { EmptyState } from "@/components/EmptyState";
import { Icon } from "@/components/Icon";
import { TeamPhoto } from "@/components/EditablePhoto";
import { CategoryTag } from "@/components/schedule/CategoryTag";
import { descendants } from "@/lib/hierarchy";
import { ROLE_LABEL, type Role } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { canEditTeam, teamPage } from "@/lib/teams";

export const metadata = { title: "Team" };

const STATUS: Record<string, string> = { dnf: "DNF", dns: "DNS", dsq: "DSQ" };
const ROW = "flex items-center gap-4 rounded-xl px-2 py-3 transition-colors hover:bg-snow/5";

/**
 * A team's page, for anyone signed in: its photo and name, where it stands in each category it runs this season, its
 * people, and its drivers' latest results.
 */
export default async function TeamPage({ params }: { params: Promise<{ id: string }> }) {
  const me = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const page = await teamPage(id);
  if (!page) notFound();
  const { team, categories, people, standings, results } = page;
  const editable = await canEditTeam(me, team.id);
  // A person's own page opens only for those below the viewer (as on People), and for the viewer themself.
  const below = people.length > 0 ? new Set((await descendants(me)).map((u) => u.id)) : new Set<string>();
  const canOpen = (personId: string) => personId === me.id || below.has(personId);
  const staff = me.role === "admin" || me.role === "coordinator";
  const categoryOf = (categoryId: string | null) => categories.find((c) => c.id === categoryId);

  const line = [
    people.length === 1 ? "1 person" : `${people.length} people`,
    categories.length > 0 ? `races in ${categories.map((c) => c.code).join(", ")}` : null,
  ]
    .filter(Boolean)
    .join(" · ");
  const latest = results.slice(0, 10);

  return (
    <div className="flat mx-auto max-w-2xl">
      <Link href={staff ? "/people/teams" : "/standings"} className="btn-icon -ml-2 mb-2" aria-label="Back">
        <Icon name="back" />
      </Link>
      <ProfileBanner
        photo={team.photoUrl}
        name={team.name}
        role={line}
        status={null}
        photoSlot={editable ? <TeamPhoto teamId={team.id} src={team.photoUrl} name={team.name} size={80} /> : <Avatar src={team.photoUrl} name={team.name} size={80} sayNone />}
      />

      <GroupTitle title="Standings" count={standings.length > 0 ? standings.length : undefined} />
      {standings.length === 0 ? (
        <EmptyState section icon="star" title="No standings yet" line="The team shows here once it has results this season." />
      ) : (
        standings.map((s) => (
          <Link key={s.category.id} href={`/standings?category=${s.category.id}`} className={ROW}>
            <span className="min-w-0 flex-1">
              <span className="flex items-center gap-2">
                <CategoryTag category={s.category} />
                <span className="truncate text-sm text-snow-soft">{s.category.name}</span>
              </span>
              <span className="mt-0.5 block text-2xl font-bold">
                P{s.position} <span className="text-base font-semibold text-snow-faint">of {s.of}</span>
              </span>
              <span className="block text-sm text-snow-soft">
                {s.wins === 1 ? "1 win" : `${s.wins} wins`} · {s.podiums === 1 ? "1 podium" : `${s.podiums} podiums`}
              </span>
            </span>
            <span className="shrink-0 text-right">
              <span className="block text-xl font-bold text-gold">{s.points}</span>
              <span className="block text-xs text-snow-faint">pts</span>
            </span>
            <Icon name="chevronRight" className="h-5 w-5 shrink-0 text-gold" />
          </Link>
        ))
      )}

      <GroupTitle title="People" count={people.length} />
      {people.length === 0 && (
        <EmptyState
          section
          icon="people"
          title="No one in this team yet"
          line={staff ? "Add its manager, drivers and crew from People." : "Its manager, drivers and crew show here once they're added."}
          action={staff ? { label: "Add someone", href: "/people/new" } : undefined}
        />
      )}
      {people.map((p) => {
        const body = (
          <>
            <Avatar src={p.photoUrl} name={p.name} size={46} />
            <span className="min-w-0 flex-1">
              <span className="block truncate font-bold">{p.name}</span>
              <span className="block truncate text-sm text-snow-soft">{ROLE_LABEL[p.role as Role] ?? p.role}</span>
              {p.pending && <span className="block text-sm text-gold">Invite not accepted</span>}
            </span>
          </>
        );
        return canOpen(p.id) ? (
          <Link key={p.id} href={`/people/${p.id}`} className={ROW}>
            {body}
            <Icon name="chevronRight" className="h-5 w-5 shrink-0 text-gold" />
          </Link>
        ) : (
          <div key={p.id} className="flex items-center gap-4 px-2 py-3">
            {body}
          </div>
        );
      })}

      <GroupTitle title="Latest results" />
      {latest.length === 0 && <EmptyState section icon="star" title="No results yet" line="Its drivers' finishes this season show here." />}
      {latest.map((r, i) => {
        const category = categoryOf(r.categoryId);
        const finished = r.status === "finished";
        return (
          <Link key={`${r.sessionId}-${i}`} href={`/results/${r.sessionId}`} className={ROW}>
            <span className={`w-12 shrink-0 text-center text-xl font-bold ${finished ? (r.position === 1 ? "text-gold" : "") : "text-danger"}`}>
              {finished ? `P${r.position ?? "–"}` : (STATUS[r.status] ?? r.status)}
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex items-center gap-2">
                {category && <CategoryTag category={category} />}
                <span className="truncate font-bold">
                  {r.sessionName} · {r.weekendName}
                </span>
              </span>
              <span className="block truncate text-sm text-snow-soft">{r.driverName}</span>
            </span>
            <span className={`shrink-0 text-sm font-semibold ${r.points > 0 ? "" : "text-snow-faint"}`}>{r.points} pts</span>
            <Icon name="chevronRight" className="h-5 w-5 shrink-0 text-gold" />
          </Link>
        );
      })}
    </div>
  );
}
