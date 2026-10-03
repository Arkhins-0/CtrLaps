import "server-only";

import { after } from "next/server";
import { AuthError, type SessionUser } from "./auth";
import { one, q, run, tx } from "./db";
import { groupEvent } from "./messages";
import { LIVE_SEASON } from "./seasons";
import { pushSync, pushTo } from "./push";
import { userById } from "./users";

/*
 * Staff groups, of two kinds, each with a group chat (an ordinary group conversation, so it has every group feature)
 * whose members always follow the group — nobody is added, removed or leaves by hand (groups.ts refuses):
 *
 * - a **volunteer group**: volunteers led by a coordinator. In its chat: its volunteers, its lead coordinator and every
 *   admin. Seen and managed only by its lead coordinator and admins.
 * - a **delegation** (JK Tyre, FMSCI…): delegates (role race_official). No lead: every admin and coordinator is in its
 *   chat and manages it.
 *
 * Whoever manages a group renames it, opens or closes its chat, moves its people to another group of the same kind,
 * and keeps a member from sending (`no_messages`) or from doing anything (`read_only`). A volunteer group can also be
 * handed to another coordinator. A person's group is users.volunteer_group_id for both kinds.
 */

export type GroupKind = "volunteer" | "delegation";
export const isGroupKind = (v: unknown): v is GroupKind => v === "volunteer" || v === "delegation";

/** Whose group each kind is, and the words for it. */
const MEMBER_ROLE: Record<GroupKind, string> = { volunteer: "volunteer", delegation: "race_official" };
const WORDS: Record<GroupKind, { group: string; Group: string; one: string; many: string }> = {
  volunteer: { group: "volunteer group", Group: "Volunteer group", one: "volunteer", many: "volunteers" },
  delegation: { group: "delegation", Group: "Delegation", one: "delegate", many: "delegates" },
};
const kindOfRole = (role: string): GroupKind | null => (role === "volunteer" ? "volunteer" : role === "race_official" ? "delegation" : null);

export type VolunteerGroup = {
  id: string;
  kind: GroupKind;
  name: string;
  open: boolean;
  conversationId: string;
  coordinator: { id: string; name: string } | null;
  volunteers: number;
  unread: number;
  lastMessage: string | null;
  lastMessageAt: string | null;
  /** The viewer manages it. */
  canManage: boolean;
};

export type Permission = "full" | "no_messages" | "read_only";
export const isPermission = (v: unknown): v is Permission => v === "full" || v === "no_messages" || v === "read_only";

type GroupRow = { id: string; kind: GroupKind; name: string; open: boolean; conversation_id: string; coordinator_id: string | null };
const GROUP_COLUMNS = "id, kind, name, open, conversation_id, coordinator_id";

export async function volunteerGroup(id: string): Promise<GroupRow | undefined> {
  return one<GroupRow>(`SELECT ${GROUP_COLUMNS} FROM volunteer_groups WHERE id = $1`, [id]);
}

/** The staff group whose chat this conversation is, if it is one. */
export async function volunteerGroupOfChat(conversationId: string): Promise<GroupRow | undefined> {
  return one<GroupRow>(`SELECT ${GROUP_COLUMNS} FROM volunteer_groups WHERE conversation_id = $1`, [conversationId]);
}

/** Admins manage every group; a coordinator every delegation, and the volunteer groups they lead. */
export const managesGroup = (user: Pick<SessionUser, "id" | "role">, g: Pick<GroupRow, "coordinator_id" | "kind">): boolean =>
  user.role === "admin" || (user.role === "coordinator" && (g.kind === "delegation" || g.coordinator_id === user.id));

async function requireManager(user: SessionUser, groupId: string): Promise<GroupRow> {
  const g = await volunteerGroup(groupId);
  // Other coordinators don't see someone else's volunteer group at all.
  if (!g || !managesGroup(user, g)) throw new AuthError(404, "No such group.");
  return g;
}

/**
 * Make a group chat's members match its group: its people (members), and as the chat's admins every active admin,
 * plus the lead coordinator (a volunteer group) or every active coordinator (a delegation). Anyone else goes. A
 * member's permission is kept. Returns who changed, for a refresh.
 */
export async function syncVolunteerGroup(groupId: string): Promise<string[]> {
  const g = await volunteerGroup(groupId);
  if (!g) return [];
  return tx(async (c) => {
    const want = (
      await c.query<{ id: string; role: string }>(
        `SELECT u.id, CASE WHEN u.role = $3 THEN 'member' ELSE 'admin' END AS role FROM users u
         WHERE u.status IN ('active', 'pending', 'suspended')
           AND ((u.role = $3 AND u.volunteer_group_id = $1)
             OR u.id = $2
             OR (u.status = 'active' AND (u.role = 'admin' OR ($4 AND u.role = 'coordinator'))))`,
        [groupId, g.coordinator_id, MEMBER_ROLE[g.kind], g.kind === "delegation"],
      )
    ).rows;
    const have = (await c.query<{ user_id: string; role: string }>("SELECT user_id, role FROM group_members WHERE conversation_id = $1", [g.conversation_id])).rows;
    const wantIds = new Set(want.map((w) => w.id));
    const changed: string[] = [];
    const gone = have.filter((h) => !wantIds.has(h.user_id)).map((h) => h.user_id);
    if (gone.length) {
      await c.query("DELETE FROM group_members WHERE conversation_id = $1 AND user_id = ANY($2::uuid[])", [g.conversation_id, gone]);
      changed.push(...gone);
    }
    for (const w of want) {
      const h = have.find((x) => x.user_id === w.id);
      if (!h) {
        await c.query("INSERT INTO group_members (conversation_id, user_id, role) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING", [g.conversation_id, w.id, w.role]);
        changed.push(w.id);
      } else if (h.role !== w.role) {
        await c.query("UPDATE group_members SET role = $3 WHERE conversation_id = $1 AND user_id = $2", [g.conversation_id, w.id, w.role]);
        changed.push(w.id);
      }
    }
    return changed;
  });
}

/** Every group at once: after a change that may touch any of them (someone made an admin or coordinator, a role changed). */
export async function syncAllVolunteerGroups(): Promise<void> {
  const groups = await q<{ id: string; conversation_id: string }>("SELECT id, conversation_id FROM volunteer_groups");
  for (const g of groups) {
    const changed = await syncVolunteerGroup(g.id);
    if (changed.length) after(() => pushSync(changed, { scope: "chat", id: g.conversation_id }));
  }
}

/**
 * The groups of a kind this person sees. Volunteer groups: admins all, a coordinator those they lead, a volunteer
 * their own. Delegations: admins and coordinators all, a delegate their own.
 */
export async function listVolunteerGroups(user: SessionUser, kind: GroupKind = "volunteer"): Promise<VolunteerGroup[]> {
  const member = MEMBER_ROLE[kind];
  if (!["admin", "coordinator", member].includes(user.role)) return [];
  const STAFF = "(NOT m.staff_only OR EXISTS (SELECT 1 FROM group_members ga WHERE ga.conversation_id = m.conversation_id AND ga.user_id = $1 AND ga.role = 'admin'))";
  const rows = await q<{
    id: string;
    name: string;
    open: boolean;
    conversation_id: string;
    coordinator_id: string | null;
    coordinator_name: string | null;
    volunteers: string;
    unread: string;
    last_body: string | null;
    last_at: string | null;
  }>(
    `SELECT g.id, g.name, g.open, g.conversation_id, g.coordinator_id, COALESCE(NULLIF(cu.name, ''), cu.email) AS coordinator_name,
            (SELECT count(*) FROM users v WHERE v.volunteer_group_id = g.id AND v.role = $4 AND v.status <> 'deleted')::text AS volunteers,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = g.conversation_id AND r.read_at IS NULL AND ${LIVE_SEASON("m")})::text AS unread,
            (SELECT CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted'
                         WHEN m.event IS NOT NULL THEN m.body
                         ELSE COALESCE(NULLIF(u.name, ''), u.email, 'Someone') || ': ' || CASE WHEN m.body <> '' THEN m.body ELSE 'Attachment' END END
               FROM messages m LEFT JOIN users u ON u.id = m.sender_id
               WHERE m.conversation_id = g.conversation_id AND ${LIVE_SEASON("m")} AND ${STAFF} ORDER BY m.created_at DESC LIMIT 1) AS last_body,
            (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = g.conversation_id AND ${LIVE_SEASON("m")} AND ${STAFF}) AS last_at
     FROM volunteer_groups g LEFT JOIN users cu ON cu.id = g.coordinator_id
     WHERE g.kind = $3 AND (
           $2 = 'admin'
        OR ($2 = 'coordinator' AND ($3 = 'delegation' OR g.coordinator_id = $1))
        OR ($2 = $4 AND g.id = (SELECT volunteer_group_id FROM users WHERE id = $1)))
     ORDER BY lower(g.name)`,
    [user.id, user.role, kind, member],
  );
  return rows.map((r) => ({
    id: r.id,
    kind,
    name: r.name,
    open: r.open,
    conversationId: r.conversation_id,
    coordinator: r.coordinator_id ? { id: r.coordinator_id, name: r.coordinator_name ?? "Coordinator" } : null,
    volunteers: Number(r.volunteers),
    unread: Number(r.unread),
    lastMessage: r.last_body ? (r.last_body.length > 140 ? `${r.last_body.slice(0, 137)}…` : r.last_body) : null,
    lastMessageAt: r.last_at ? new Date(r.last_at).toISOString() : null,
    canManage: managesGroup(user, { coordinator_id: r.coordinator_id, kind }),
  }));
}

export type GroupDetail = {
  id: string;
  kind: GroupKind;
  name: string;
  open: boolean;
  conversationId: string;
  coordinator: { id: string; name: string } | null;
  /** Its people: volunteers, or delegates. */
  volunteers: { id: string; name: string; photoUrl: string | null; permission: Permission; status: string }[];
  /** People of its kind in no group, who can be brought in. */
  unassigned: { id: string; name: string }[];
  /** Where its people can be moved: the other groups of its kind. */
  otherGroups: { id: string; name: string; coordinator: string | null }[];
  /** Who can lead it (volunteer groups only). */
  coordinators: { id: string; name: string }[];
};

/** One group, for whoever manages it. */
export async function volunteerGroupDetail(user: SessionUser, groupId: string): Promise<GroupDetail> {
  const g = await requireManager(user, groupId);
  const member = MEMBER_ROLE[g.kind];
  const [lead, volunteers, unassigned, others, coordinators] = await Promise.all([
    g.coordinator_id ? userById(g.coordinator_id) : Promise.resolve(undefined),
    q<{ id: string; name: string; photo_key: string | null; permission: Permission | null; status: string }>(
      `SELECT u.id, COALESCE(NULLIF(u.name, ''), u.email) AS name, u.photo_key, gm.permission, u.status
       FROM users u LEFT JOIN group_members gm ON gm.conversation_id = $2 AND gm.user_id = u.id
       WHERE u.role = $3 AND u.volunteer_group_id = $1 AND u.status <> 'deleted' ORDER BY lower(COALESCE(NULLIF(u.name, ''), u.email))`,
      [groupId, g.conversation_id, member],
    ),
    q<{ id: string; name: string }>(
      `SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users
       WHERE role = $1 AND volunteer_group_id IS NULL AND status NOT IN ('deleted', 'banned', 'dismissed') ORDER BY lower(COALESCE(NULLIF(name, ''), email))`,
      [member],
    ),
    q<{ id: string; name: string; coordinator: string | null }>(
      `SELECT g.id, g.name, COALESCE(NULLIF(u.name, ''), u.email) AS coordinator FROM volunteer_groups g LEFT JOIN users u ON u.id = g.coordinator_id
       WHERE g.id <> $1 AND g.kind = $2 ORDER BY lower(g.name)`,
      [groupId, g.kind],
    ),
    g.kind === "volunteer"
      ? q<{ id: string; name: string }>(
          `SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY lower(COALESCE(NULLIF(name, ''), email))`,
        )
      : Promise.resolve([]),
  ]);
  return {
    id: g.id,
    kind: g.kind,
    name: g.name,
    open: g.open,
    conversationId: g.conversation_id,
    coordinator: lead ? { id: lead.id, name: lead.name || lead.email } : null,
    volunteers: volunteers.map((v) => ({
      id: v.id,
      name: v.name,
      photoUrl: v.photo_key ? `/api/users/${v.id}/photo?v=${encodeURIComponent(v.photo_key)}` : null,
      permission: v.permission ?? "full",
      status: v.status,
    })),
    unassigned,
    otherGroups: others,
    coordinators,
  };
}

async function makeGroup(kind: GroupKind, name: string, lead: string | null, by: string): Promise<string> {
  return tx(async (c) => {
    const conv = (await c.query<{ id: string }>("INSERT INTO conversations (kind, name, created_by) VALUES ('group', $1, $2) RETURNING id", [name, by])).rows[0];
    return (await c.query<{ id: string }>("INSERT INTO volunteer_groups (kind, name, coordinator_id, conversation_id) VALUES ($1, $2, $3, $4) RETURNING id", [kind, name, lead, conv.id])).rows[0].id;
  });
}

/**
 * A new group. A volunteer group is led by the coordinator making it (an admin names the coordinator); a delegation
 * has no lead.
 */
export async function createVolunteerGroup(user: SessionUser, name: string, coordinatorId: string | null, kind: GroupKind = "volunteer"): Promise<string> {
  if (user.role !== "admin" && user.role !== "coordinator") throw new AuthError(403, `Only coordinators and admins make ${WORDS[kind].group}s.`);
  const clean = name.trim().replace(/\s+/g, " ").slice(0, 80);
  if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
  const lead = kind === "delegation" ? null : user.role === "coordinator" ? user.id : coordinatorId;
  if (kind === "volunteer") {
    if (!lead) throw new AuthError(400, "Pick the coordinator who leads it.");
    const c = await userById(lead);
    if (!c || c.role !== "coordinator" || c.status !== "active") throw new AuthError(400, "A volunteer group is led by an active coordinator.");
  }
  const id = await makeGroup(kind, clean, lead, user.id);
  await syncVolunteerGroup(id);
  const g = (await volunteerGroup(id))!;
  await groupEvent(g.conversation_id, user.id, `${user.name || user.email} made the ${WORDS[kind].group} "${clean}"`);
  return id;
}

/** Rename, open or close the chat, or (a volunteer group) hand it to another coordinator. */
export async function updateVolunteerGroup(user: SessionUser, groupId: string, changes: { name?: string; coordinatorId?: string | null; open?: boolean }): Promise<void> {
  const g = await requireManager(user, groupId);
  const who = user.name || user.email;
  if (changes.name !== undefined) {
    const clean = changes.name.trim().replace(/\s+/g, " ").slice(0, 80);
    if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
    if (clean !== g.name) {
      await run("UPDATE volunteer_groups SET name = $2 WHERE id = $1", [groupId, clean]);
      await run("UPDATE conversations SET name = $2 WHERE id = $1", [g.conversation_id, clean]);
      await groupEvent(g.conversation_id, user.id, `${who} renamed the group to "${clean}"`);
    }
  }
  if (changes.coordinatorId !== undefined && changes.coordinatorId !== g.coordinator_id) {
    if (g.kind !== "volunteer") throw new AuthError(400, "A delegation has no lead coordinator.");
    if (!changes.coordinatorId) throw new AuthError(400, "Pick the coordinator who leads it.");
    const lead = await userById(changes.coordinatorId);
    if (!lead || lead.role !== "coordinator" || lead.status !== "active") throw new AuthError(400, "A volunteer group is led by an active coordinator.");
    await run("UPDATE volunteer_groups SET coordinator_id = $2 WHERE id = $1", [groupId, lead.id]);
    // Its volunteers now report to the new coordinator (their private chat with "their coordinator" follows).
    await run("UPDATE users SET parent_id = $2 WHERE role = 'volunteer' AND volunteer_group_id = $1", [groupId, lead.id]);
    await groupEvent(g.conversation_id, user.id, `${who} handed the group to ${lead.name || lead.email}`);
  }
  if (changes.open !== undefined && changes.open !== g.open) {
    await run("UPDATE volunteer_groups SET open = $2 WHERE id = $1", [groupId, changes.open]);
    await groupEvent(g.conversation_id, user.id, changes.open ? `${who} opened the chat` : `${who} closed the chat`);
  }
  const changed = await syncVolunteerGroup(groupId);
  const members = (await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1", [g.conversation_id])).map((m) => m.user_id);
  after(() => pushSync(Array.from(new Set([...members, ...changed])), { scope: "chat", id: g.conversation_id }));
}

/** Tell people they are in a group now, and bring their chat lists up to date. */
function welcome(ids: string[], to: GroupRow, lead: { name: string | null; email: string } | undefined) {
  after(() =>
    pushTo(ids, {
      title: WORDS[to.kind].Group,
      body: `You're now in ${to.name}${lead ? `, with ${lead.name || lead.email}` : ""}.`,
      link: `/chats/${to.conversation_id}`,
      tag: `vg-${to.id}`,
    }),
  );
}

/**
 * Put a volunteer or a delegate in a group of their kind (or in none, `toGroupId` null). Delegates: any admin or
 * coordinator. Volunteers: an admin; the coordinator of the group they are leaving; or, for one in no group, the
 * coordinator of the group they join.
 */
export async function moveVolunteer(user: SessionUser, personId: string, toGroupId: string | null): Promise<void> {
  const v = await one<{ id: string; name: string | null; email: string; role: string; volunteer_group_id: string | null }>(
    "SELECT id, name, email, role, volunteer_group_id FROM users WHERE id = $1",
    [personId],
  );
  const kind = v ? kindOfRole(v.role) : null;
  if (!v || !kind) throw new AuthError(404, "No such volunteer or delegate.");
  const from = v.volunteer_group_id ? await volunteerGroup(v.volunteer_group_id) : undefined;
  const to = toGroupId ? await volunteerGroup(toGroupId) : undefined;
  if (toGroupId && (!to || to.kind !== kind)) throw new AuthError(404, "No such group.");
  if (from?.id === to?.id) return;
  const allowed =
    user.role === "admin" ||
    (user.role === "coordinator" && (kind === "delegation" || (from ? from.coordinator_id === user.id : Boolean(to && to.coordinator_id === user.id))));
  if (!allowed) throw new AuthError(403, kind === "delegation" ? "Only admins and coordinators move delegates." : "Only an admin, or the coordinator of the group they are leaving, can move a volunteer.");
  await run("UPDATE users SET volunteer_group_id = $2, parent_id = COALESCE($3, parent_id) WHERE id = $1", [v.id, to?.id ?? null, kind === "volunteer" ? (to?.coordinator_id ?? null) : null]);
  const name = v.name || v.email;
  if (from) {
    await syncVolunteerGroup(from.id);
    await groupEvent(from.conversation_id, user.id, `${name} moved to another group`, true);
  }
  if (to) {
    await syncVolunteerGroup(to.id);
    await groupEvent(to.conversation_id, user.id, `${name} joined the group`);
    welcome([v.id], to, to.coordinator_id ? await userById(to.coordinator_id) : undefined);
  }
  after(() => pushSync([v.id], { scope: "chat", id: (to ?? from)!.conversation_id }));
}

/**
 * Move several of a group's people at once to another group of its kind (or to none): whoever manages the group.
 * One line in each chat, one push per person. Returns how many moved.
 */
export async function moveVolunteers(user: SessionUser, fromGroupId: string, userIds: string[], toGroupId: string | null): Promise<number> {
  const from = await volunteerGroup(fromGroupId);
  if (!from || !managesGroup(user, from)) throw new AuthError(404, "No such group.");
  const to = toGroupId ? await volunteerGroup(toGroupId) : undefined;
  if (toGroupId && (!to || to.kind !== from.kind)) throw new AuthError(404, "No such group.");
  if (to && to.id === from.id) throw new AuthError(400, "They are in that group already.");
  const words = WORDS[from.kind];
  const ids = Array.from(new Set(userIds));
  const people = ids.length
    ? await q<{ id: string; name: string | null; email: string }>(
        "SELECT id, name, email FROM users WHERE id = ANY($1::uuid[]) AND role = $3 AND volunteer_group_id = $2",
        [ids, from.id, MEMBER_ROLE[from.kind]],
      )
    : [];
  if (people.length === 0) throw new AuthError(400, `Pick at least one ${words.one} of this group.`);
  await run("UPDATE users SET volunteer_group_id = $2, parent_id = COALESCE($3, parent_id) WHERE id = ANY($1::uuid[])", [
    people.map((p) => p.id),
    to?.id ?? null,
    from.kind === "volunteer" ? (to?.coordinator_id ?? null) : null,
  ]);
  await syncVolunteerGroup(from.id);
  if (to) await syncVolunteerGroup(to.id);
  const who = user.name || user.email;
  const names = people.length <= 3 ? people.map((p) => p.name || p.email).join(", ") : `${people.length} ${words.many}`;
  await groupEvent(from.conversation_id, user.id, to ? `${who} moved ${names} to ${to.name}` : `${who} took ${names} out of the group`, true);
  if (to) {
    await groupEvent(to.conversation_id, user.id, `${names} joined from ${from.name}`);
    welcome(people.map((p) => p.id), to, to.coordinator_id ? await userById(to.coordinator_id) : undefined);
  }
  after(() => pushSync(people.map((p) => p.id), { scope: "chat", id: (to ?? from).conversation_id }));
  return people.length;
}

/** Keep one of a group's people from sending in its chat, or from doing anything; or let them again. */
export async function setPermission(user: SessionUser, conversationId: string, memberId: string, permission: Permission): Promise<void> {
  const g = await volunteerGroupOfChat(conversationId);
  if (!g) throw new AuthError(400, "Only volunteer group and delegation chats have this.");
  if (!managesGroup(user, g)) throw new AuthError(403, "Only whoever manages this group can do that.");
  const target = await one<{ role: string; name: string | null; email: string }>(
    `SELECT u.role, u.name, u.email FROM group_members gm JOIN users u ON u.id = gm.user_id WHERE gm.conversation_id = $1 AND gm.user_id = $2`,
    [conversationId, memberId],
  );
  if (!target) throw new AuthError(404, "Not in this chat.");
  if (target.role !== MEMBER_ROLE[g.kind]) throw new AuthError(400, `Only ${WORDS[g.kind].many} can be limited here.`);
  await run("UPDATE group_members SET permission = $3 WHERE conversation_id = $1 AND user_id = $2", [conversationId, memberId, permission]);
  const name = target.name || target.email;
  await groupEvent(
    conversationId,
    user.id,
    permission === "full" ? `${name} can chat again` : permission === "no_messages" ? `${name} can no longer send messages` : `${name} can now only read the chat`,
    true,
  );
  after(async () => pushSync((await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1", [conversationId])).map((m) => m.user_id), { scope: "chat", id: conversationId }));
}

/** A volunteer made or promoted by a coordinator joins that coordinator's first volunteer group (made if they lead none). */
export async function placeNewVolunteer(volunteerId: string, coordinatorId: string): Promise<void> {
  const lead = await userById(coordinatorId);
  if (!lead || lead.role !== "coordinator") return;
  let g = await one<{ id: string }>("SELECT id FROM volunteer_groups WHERE kind = 'volunteer' AND coordinator_id = $1 ORDER BY created_at LIMIT 1", [coordinatorId]);
  if (!g) g = { id: await makeGroup("volunteer", `${lead.name || lead.email}'s volunteers`, lead.id, lead.id) };
  await run("UPDATE users SET volunteer_group_id = $2 WHERE id = $1", [volunteerId, g.id]);
  await syncVolunteerGroup(g.id);
}
