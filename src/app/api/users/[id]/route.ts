import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser, revokeAll } from "@/lib/auth";
import { q, run } from "@/lib/db";
import { canEdit, isBelow } from "@/lib/hierarchy";
import { fail, json } from "@/lib/http";
import { isDeveloper, isStatus } from "@/lib/roles";
import { categoriesOf } from "@/lib/categories";
import { currentSeason } from "@/lib/seasons";
import { assignedCategories, syncTeamIds, teamCategories } from "@/lib/teams";
import { one } from "@/lib/db";
import { audit, qrUrl, toPublic, userById } from "@/lib/users";
import { deletionDue } from "@/lib/accountDeletion";

export const dynamic = "force-dynamic";

/** One person: yourself, or anyone below you. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user) return fail("No such person.", 404);
  if (user.id !== me.id && !(await isBelow(me, user.id))) return fail("Not allowed.", 403);
  const season = (await currentSeason()).id;
  const team = (await one<{ team_id: string | null }>("SELECT team_id FROM users WHERE id = $1", [id]))?.team_id ?? null;
  const assignable = user.role === "racer" || user.role === "race_official";
  return json({
    user: toPublic(user),
    qrUrl: qrUrl(user),
    canEdit: user.id !== me.id && canEdit(me, user),
    // Race categories: the season's list, the ones given to them, their team's entries, and who may change them.
    raceCategories: await categoriesOf([season]),
    categoryIds: assignable ? await assignedCategories(id, season) : [],
    teamCategoryIds: await teamCategories(team, season),
    canSetCategories: assignable && user.id !== me.id && (me.role === "admin" || me.role === "coordinator" || canEdit(me, user)),
    // A developer may make another active admin a developer, or take it back.
    canSetDev: isDeveloper(me) && user.id !== me.id && user.role === "admin" && user.status === "active",
    // A developer may delete someone's account on their request (7 days' wait); when one is waiting, its date.
    canDelete: isDeveloper(me) && user.id !== me.id && user.status !== "deleted",
    deletionDueAt: isDeveloper(me) ? await deletionDue(user.id) : null,
  });
});

/**
 * Change a locked profile, a status, or (admin) which coordinator a
 * volunteer belongs to. Who may do what is decided in hierarchy.canEdit.
 */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user) return fail("No such person.", 404);
  if (!canEdit(me, user)) return fail("Only this person's manager, a coordinator or an admin can change this.", 403);
  if (user.id === me.id && me.role !== "admin") return fail("Your profile is locked.", 403);

  const b = await body(request);
  const changes: Record<string, unknown> = {};

  if (typeof b.name === "string") {
    const name = str(b.name, 120);
    if (name.length < 2) return fail("Enter a full name.");
    changes.name = name;
  }
  if (typeof b.dob === "string") {
    const dob = str(b.dob, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dob) || Number.isNaN(Date.parse(dob))) return fail("Enter a valid date of birth.");
    changes.dob = dob;
  }
  if (typeof b.phone === "string") {
    const phone = str(b.phone, 40);
    if (!/^\+?[\d\s()-]{6,}$/.test(phone)) return fail("Enter a contact number.");
    changes.phone = phone;
  }
  if (typeof b.teamName === "string" && (user.role === "team_manager" || me.role === "admin" || me.role === "coordinator")) {
    changes.team_name = str(b.teamName, 80) || null;
  }
  if (b.status !== undefined) {
    if (!isStatus(b.status) || b.status === "pending" || b.status === "deleted") return fail("Not a valid status.");
    if (user.status === "pending" && b.status !== "banned" && b.status !== "dismissed")
      return fail("The account is not set up yet; the person must accept their invite first.");
    changes.status = b.status;
  }
  if (typeof b.parentId === "string") {
    if (me.role !== "admin" || user.role !== "volunteer") return fail("Only an admin can move a volunteer.", 403);
    const parent = await userById(b.parentId);
    if (!parent || parent.role !== "coordinator") return fail("Volunteers belong to a coordinator.");
    changes.parent_id = parent.id;
  }
  if (Object.keys(changes).length === 0) return fail("Nothing to change.");

  const keys = Object.keys(changes);
  await run(
    `UPDATE users SET ${keys.map((k, i) => `${k} = $${i + 2}`).join(", ")} WHERE id = $1`,
    [user.id, ...keys.map((k) => changes[k])],
  );
  // A ban keeps the sessions so the person's phones learn of it and clear their chats; lifting one ends them.
  if (changes.status === "banned") await revokeAll(user.id, { keepSessions: true });
  else if (changes.status && (changes.status !== "active" || user.status === "banned")) await revokeAll(user.id);
  if (changes.team_name !== undefined && user.role === "team_manager") {
    // The team follows the manager: racers and crew carry the same name.
    await run("UPDATE users SET team_name = $2 WHERE parent_id = $1 AND role IN ('racer', 'crew')", [user.id, changes.team_name]);
    const people = await q<{ id: string }>("SELECT id FROM users WHERE parent_id = $1 AND role IN ('racer', 'crew')", [user.id]);
    await syncTeamIds(people.map((p) => p.id));
  }
  if (changes.team_name !== undefined) await syncTeamIds([user.id]);
  await audit(me.id, user.id, "user.updated", { changes, before: toPublic(user) });
  return json({ user: toPublic((await userById(user.id))!) });
});
