import { placeNewVolunteer, syncAllVolunteerGroups } from "@/lib/volunteers";
import { body, handle, str } from "@/lib/api";
import { issueToken, requireUser, USER_COLUMNS, type SessionUser } from "@/lib/auth";
import { APP_NAME, SITE_URL } from "@/lib/config";
import { one, run } from "@/lib/db";
import { sendInvite, sendNotice } from "@/lib/email";
import { announceCandidates, canCreateRole, canPromote, chatCandidates, descendants, groupCandidates } from "@/lib/hierarchy";
import { fail, json } from "@/lib/http";
import { isRole, ROLE_LABEL } from "@/lib/roles";
import { categoryRoster } from "@/lib/categoryChannels";
import { syncTeamIds } from "@/lib/teams";
import { audit, createUser, toPublic, userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Everyone below the signed-in person — or, with `?chat=1`, everyone they
 * may chat with; with `?group=1`, everyone they may bring into a group,
 * each marked `groupMode` "direct" or "request". `?role=` narrows it. The plain list also carries this season's
 * categories with the people tied to each (see categoryRoster), for picking an audience or filtering by category.
 */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const params = new URL(request.url).searchParams;
  const role = params.get("role");
  if (params.get("group") === "1") {
    const people = (await groupCandidates(user)).filter((x) => !role || x.user.role === role);
    return json({ users: people.map((x) => ({ ...toPublic(x.user), groupMode: x.mode })) });
  }
  // `?announce=1`: everyone an announcement may go to (admins and coordinators: every active person).
  if (params.get("announce") === "1") return json({ users: (await announceCandidates(user)).map(toPublic), categories: await categoryRoster() });
  const base = params.get("chat") === "1" ? await chatCandidates(user) : await descendants(user);
  const people = base.filter((p) => !role || p.role === role);
  if (params.get("chat") === "1") return json({ users: people.map(toPublic) });
  return json({ users: people.map(toPublic), categories: await categoryRoster() });
});

/**
 * Give someone a role. A new email gets an account under the signed-in person
 * and an invite; an email that already has an account is promoted instead, when
 * the signed-in person may (see canPromote), and is told by email.
 */
export const POST = handle(async (request) => {
  const creator = await requireUser();
  const b = await body(request);
  const email = str(b.email, 200).toLowerCase();
  const role = b.role;
  const typedTeam = str(b.teamName, 80) || null;
  if (!isRole(role) || !canCreateRole(creator.role, role)) return fail("You cannot give that role.", 403);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail("Enter a valid email address.");
  if (role === "team_manager" && !typedTeam) return fail("Enter the team name.");
  // Racers and crew take their team manager's team; anyone else giving the role may type one.
  const teamFor = (current: string | null): string | null =>
    role === "team_manager"
      ? typedTeam
      : role === "racer" || role === "crew"
        ? (creator.role === "team_manager" ? creator.team_name : typedTeam ?? current)
        : null;

  const existing = await userByEmail(email);
  if (existing) {
    if (!canPromote(creator, existing)) {
      return fail("That email already has an account you can't change. Ask their manager or an admin.", 409);
    }
    const updated = await one<SessionUser>(
      `UPDATE users SET role = $2, parent_id = $3, team_name = $4 WHERE id = $1 RETURNING ${USER_COLUMNS}`,
      [existing.id, role, creator.id, teamFor(existing.team_name)],
    );
    await syncTeamIds([existing.id]);
    // Volunteer groups: a volunteer joins their coordinator's group; anyone leaving the role leaves theirs; a new admin joins every chat.
    if ((existing.role === "volunteer" || existing.role === "race_official") && role !== existing.role) await run("UPDATE users SET volunteer_group_id = NULL WHERE id = $1", [existing.id]);
    if (role === "volunteer" && creator.role === "coordinator") await placeNewVolunteer(existing.id, creator.id);
    await syncAllVolunteerGroups();
    await audit(creator.id, existing.id, "user.promoted", { from: existing.role, to: role });
    await sendNotice(
      [{ email: existing.email, name: existing.name }],
      `You are now a ${ROLE_LABEL[role]} on ${APP_NAME}`,
      `You are now a ${ROLE_LABEL[role]}`,
      `${creator.name || creator.email} made you a ${ROLE_LABEL[role]} on ${APP_NAME}.`,
      SITE_URL,
      [],
      { eyebrow: "Account" },
    ).catch((error) => console.error("[promote]", error));
    return json({ user: toPublic(updated!), promoted: true });
  }

  const user = await createUser({ email, role, parentId: creator.id, createdBy: creator.id, teamName: teamFor(null) });
  await syncTeamIds([user.id]);
  if (role === "volunteer" && creator.role === "coordinator") await placeNewVolunteer(user.id, creator.id);
  if (role === "admin" || role === "coordinator") await syncAllVolunteerGroups();
  const token = await issueToken(user.id, "invite", 24 * 7);
  await sendInvite({ email: user.email }, token, creator.name || creator.email, ROLE_LABEL[role]).catch((error) =>
    console.error("[invite]", error),
  );
  await audit(creator.id, user.id, "user.created", { role });
  return json({ user: toPublic(user), promoted: false }, 201);
});
