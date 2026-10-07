import { notFound } from "next/navigation";
import Link from "next/link";
import QRCode from "qrcode";
import { DetailRow, ProfileBanner, SectionHeading } from "@/components/AppUI";
import { Avatar } from "@/components/Avatar";
import { Icon } from "@/components/Icon";
import { CopyButton } from "@/components/CopyButton";
import { DeletePersonAccount } from "@/components/DeleteAccount";
import { deletionDue } from "@/lib/accountDeletion";
import { DeveloperToggle } from "@/components/DeveloperToggle";
import { PersonActions, PersonStatus } from "@/components/PersonActions";
import { PromoteForm } from "@/components/PromoteForm";
import { StatusBadge } from "@/components/StatusBadge";
import { canChat, canEdit, canPromote, isBelow } from "@/lib/hierarchy";
import { CREATE_RULES, isDeveloper } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { qrUrl, toPublic, userById } from "@/lib/users";
import { one, q } from "@/lib/db";
import { RaceCategories } from "@/components/RaceCategories";
import { categoriesOf } from "@/lib/categories";
import { currentSeason } from "@/lib/seasons";
import { assignedCategories, teamCategories } from "@/lib/teams";
import { listVolunteerGroups } from "@/lib/volunteers";

export const metadata = { title: "Person" };

export default async function Person({ params }: { params: Promise<{ id: string }> }) {
  const me = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const user = await userById(id);
  if (!user || (user.id !== me.id && !(await isBelow(me, user.id)))) notFound();
  const p = toPublic(user);
  const editable = user.id !== me.id && canEdit(me, user);
  const season = (await currentSeason()).id;
  const teamRow = await one<{ team_id: string | null }>("SELECT team_id FROM users WHERE id = $1", [user.id]);
  const [raceCategories, assigned, teamIds] = await Promise.all([
    categoriesOf([season]),
    assignedCategories(user.id, season),
    teamCategories(teamRow?.team_id ?? null, season),
  ]);
  const teamNames = (await q<{ name: string }>("SELECT name FROM teams ORDER BY lower(name)")).map((t) => t.name);
  const svg = await QRCode.toString(qrUrl(user), { type: "svg", margin: 1, color: { dark: "#0B0B0C", light: "#FFFFFF" } });
  const coordinators =
    me.role === "admin" && user.role === "volunteer"
      ? await q<{ id: string; name: string | null; email: string }>("SELECT id, name, email FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY name")
      : [];

  const promoteRoles = canPromote(me, user) ? (CREATE_RULES[me.role] ?? []) : [];
  // A new delegate can be put in a delegation as they are made one.
  const delegations = promoteRoles.includes("race_official") ? (await listVolunteerGroups(me, "delegation")).map((g) => ({ id: g.id, name: g.name })) : [];

  const name = p.name ?? p.email;

  return (
    <div className="flat mx-auto max-w-2xl">
      <Link href="/people" className="btn-icon -ml-2 mb-2" aria-label="Back">
        <Icon name="back" />
      </Link>
      <ProfileBanner
        photo={p.photoUrl}
        name={name}
        role={`${p.roleLabel}${p.teamName ? ` · ${p.teamName}` : ""}`}
        status={<StatusBadge status={p.status} />}
        // The photo only shows here; it is changed in Edit, with the rest of their profile.
        photoSlot={<Avatar src={p.photoUrl} name={name} size={80} sayNone />}
      />

      <PersonActions
        person={p}
        editable={editable}
        canChat={canChat(me, user)}
        coordinators={coordinators.map((c) => ({ id: c.id, name: c.name || c.email }))}
        qrSvg={svg}
        qrLink={qrUrl(user)}
      />

      <SectionHeading>Details</SectionHeading>
      <DetailRow icon="mail" label="Email" value={p.email} end={<CopyButton value={p.email} label="Copy email" />} />
      <DetailRow icon="call" label="Contact" value={p.phone ?? "—"} end={p.phone ? <CopyButton value={p.phone} label="Copy contact" /> : undefined} />
      <DetailRow icon="calendar" label="Date of birth" value={p.dob ?? "—"} />
      {p.teamName && (
        <DetailRow
          icon="people"
          label="Team"
          value={
            teamRow?.team_id ? (
              <Link href={`/teams/${teamRow.team_id}`} className="hover:text-gold">
                {p.teamName}
              </Link>
            ) : (
              p.teamName
            )
          }
        />
      )}
      <DetailRow icon="badge" label="Account code" value={<span className="font-mono tracking-wider">{p.verifyCode}</span>} end={<CopyButton value={p.verifyCode} label="Copy account code" />} />

      {editable && <PersonStatus person={p} />}

      {canPromote(me, user) && (
        <>
          <SectionHeading>Role</SectionHeading>
          <PromoteForm person={p} roles={promoteRoles} myRole={me.role} myTeam={me.team_name} teamNames={teamNames} delegations={delegations} />
        </>
      )}

      <RaceCategories
        userId={p.id}
        role={user.role}
        categories={raceCategories}
        initial={assigned}
        teamIds={teamIds}
        canSet={user.role === "racer" && user.id !== me.id && (me.role === "admin" || me.role === "coordinator" || canEdit(me, user))}
      />

      {isDeveloper(me) && user.id !== me.id && user.role === "admin" && user.status === "active" && <DeveloperToggle personId={p.id} isDev={p.isDev} />}

      {isDeveloper(me) && user.id !== me.id && user.status !== "deleted" && (
        <DeletePersonAccount personId={p.id} name={name} dueAt={await deletionDue(user.id)} />
      )}
    </div>
  );
}
