import "server-only";

import { after } from "next/server";
import { AuthError, type SessionUser } from "./auth";
import { one, q, run, tx } from "./db";
import { groupEvent } from "./messages";
import { LIVE_SEASON } from "./seasons";
import { pushSync, pushTo } from "./push";
import { userById } from "./users";

/*
 * Volunteer groups: a named group of volunteers led by a coordinator, with a group chat (an ordinary group
 * conversation, so it has every group feature). Its members always follow the group — its volunteers, its lead
 * coordinator and every admin — so nobody is added, removed or leaves by hand (groups.ts refuses). Only the lead
 * coordinator and admins see a group or manage it: rename it, hand it to another coordinator, open or close its chat,
 * move volunteers out, and keep a member from sending (`no_messages`) or from doing anything (`read_only`).
 */

export type VolunteerGroup = {
  id: string;
  name: string;
  open: boolean;
  conversationId: string;
  coordinator: { id: string; name: string } | null;
  volunteers: number;
  unread: number;
  lastMessage: string | null;
  lastMessageAt: string | null;
  /** The viewer leads it or is an admin: they manage it. */
  canManage: boolean;
};

export type Permission = "full" | "no_messages" | "read_only";
export const isPermission = (v: unknown): v is Permission => v === "full" || v === "no_messages" || v === "read_only";

type GroupRow = { id: string; name: string; open: boolean; conversation_id: string; coordinator_id: string | null };

export async function volunteerGroup(id: string): Promise<GroupRow | undefined> {
  return one<GroupRow>("SELECT id, name, open, conversation_id, coordinator_id FROM volunteer_groups WHERE id = $1", [id]);
}

/** The volunteer group whose chat this conversation is, if it is one. */
export async function volunteerGroupOfChat(conversationId: string): Promise<GroupRow | undefined> {
  return one<GroupRow>("SELECT id, name, open, conversation_id, coordinator_id FROM volunteer_groups WHERE conversation_id = $1", [conversationId]);
}

/** Admins manage every group; a coordinator the groups they lead. */
export const managesGroup = (user: SessionUser, g: Pick<GroupRow, "coordinator_id">): boolean =>
  user.role === "admin" || (user.role === "coordinator" && g.coordinator_id === user.id);

async function requireManager(user: SessionUser, groupId: string): Promise<GroupRow> {
  const g = await volunteerGroup(groupId);
  // Other coordinators don't see someone else's group at all.
  if (!g || !managesGroup(user, g)) throw new AuthError(404, "No such volunteer group.");
  return g;
}

/**
 * Make a group chat's members match its group: its volunteers (members), its lead coordinator and every active admin
 * (the chat's admins). Anyone else goes. A member's permission is kept. Returns who changed, for a refresh.
 */
export async function syncVolunteerGroup(groupId: string): Promise<string[]> {
  const g = await volunteerGroup(groupId);
  if (!g) return [];
  return tx(async (c) => {
    const want = (
      await c.query<{ id: string; role: string }>(
        `SELECT u.id, CASE WHEN u.role = 'volunteer' THEN 'member' ELSE 'admin' END AS role FROM users u
         WHERE u.status IN ('active', 'pending', 'suspended') AND u.status <> 'deleted'
           AND ((u.role = 'volunteer' AND u.volunteer_group_id = $1) OR u.id = $2 OR (u.role = 'admin' AND u.status = 'active'))`,
        [groupId, g.coordinator_id],
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

/** Every group at once: after a change that may touch any of them (someone made an admin, a volunteer's role changed). */
export async function syncAllVolunteerGroups(): Promise<void> {
  const groups = await q<{ id: string; conversation_id: string }>("SELECT id, conversation_id FROM volunteer_groups");
  for (const g of groups) {
    const changed = await syncVolunteerGroup(g.id);
    if (changed.length) after(() => pushSync(changed, { scope: "chat", id: g.conversation_id }));
  }
}

/** The groups this person sees: admins all; a coordinator those they lead; a volunteer their own. */
export async function listVolunteerGroups(user: SessionUser): Promise<VolunteerGroup[]> {
  if (!["admin", "coordinator", "volunteer"].includes(user.role)) return [];
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
            (SELECT count(*) FROM users v WHERE v.volunteer_group_id = g.id AND v.role = 'volunteer' AND v.status <> 'deleted')::text AS volunteers,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = g.conversation_id AND r.read_at IS NULL AND ${LIVE_SEASON("m")})::text AS unread,
            (SELECT CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted'
                         WHEN m.event IS NOT NULL THEN m.body
                         ELSE COALESCE(NULLIF(u.name, ''), u.email, 'Someone') || ': ' || CASE WHEN m.body <> '' THEN m.body ELSE 'Attachment' END END
               FROM messages m LEFT JOIN users u ON u.id = m.sender_id
               WHERE m.conversation_id = g.conversation_id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_body,
            (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = g.conversation_id AND ${LIVE_SEASON("m")}) AS last_at
     FROM volunteer_groups g LEFT JOIN users cu ON cu.id = g.coordinator_id
     WHERE $2 = 'admin' OR ($2 = 'coordinator' AND g.coordinator_id = $1)
        OR ($2 = 'volunteer' AND g.id = (SELECT volunteer_group_id FROM users WHERE id = $1))
     ORDER BY lower(g.name)`,
    [user.id, user.role],
  );
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    open: r.open,
    conversationId: r.conversation_id,
    coordinator: r.coordinator_id ? { id: r.coordinator_id, name: r.coordinator_name ?? "Coordinator" } : null,
    volunteers: Number(r.volunteers),
    unread: Number(r.unread),
    lastMessage: r.last_body ? (r.last_body.length > 140 ? `${r.last_body.slice(0, 137)}…` : r.last_body) : null,
    lastMessageAt: r.last_at ? new Date(r.last_at).toISOString() : null,
    canManage: managesGroup(user, { coordinator_id: r.coordinator_id }),
  }));
}

export type GroupDetail = {
  id: string;
  name: string;
  open: boolean;
  conversationId: string;
  coordinator: { id: string; name: string } | null;
  volunteers: { id: string; name: string; photoUrl: string | null; permission: Permission; status: string }[];
  /** Volunteers in no group, who can be brought in. */
  unassigned: { id: string; name: string }[];
  /** Where a volunteer can be moved: the other groups (names only). */
  otherGroups: { id: string; name: string; coordinator: string | null }[];
  /** Who can lead it (an admin hands it to any coordinator; its coordinator too). */
  coordinators: { id: string; name: string }[];
};

/** One group, for its coordinator or an admin. */
export async function volunteerGroupDetail(user: SessionUser, groupId: string): Promise<GroupDetail> {
  const g = await requireManager(user, groupId);
  const [lead, volunteers, unassigned, others, coordinators] = await Promise.all([
    g.coordinator_id ? userById(g.coordinator_id) : Promise.resolve(undefined),
    q<{ id: string; name: string; photo_key: string | null; permission: Permission | null; status: string }>(
      `SELECT u.id, COALESCE(NULLIF(u.name, ''), u.email) AS name, u.photo_key, gm.permission, u.status
       FROM users u LEFT JOIN group_members gm ON gm.conversation_id = $2 AND gm.user_id = u.id
       WHERE u.role = 'volunteer' AND u.volunteer_group_id = $1 AND u.status <> 'deleted' ORDER BY lower(COALESCE(NULLIF(u.name, ''), u.email))`,
      [groupId, g.conversation_id],
    ),
    q<{ id: string; name: string }>(
      `SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users
       WHERE role = 'volunteer' AND volunteer_group_id IS NULL AND status NOT IN ('deleted', 'banned', 'dismissed') ORDER BY lower(COALESCE(NULLIF(name, ''), email))`,
    ),
    q<{ id: string; name: string; coordinator: string | null }>(
      `SELECT g.id, g.name, COALESCE(NULLIF(u.name, ''), u.email) AS coordinator FROM volunteer_groups g LEFT JOIN users u ON u.id = g.coordinator_id
       WHERE g.id <> $1 ORDER BY lower(g.name)`,
      [groupId],
    ),
    q<{ id: string; name: string }>(
      `SELECT id, COALESCE(NULLIF(name, ''), email) AS name FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY lower(COALESCE(NULLIF(name, ''), email))`,
    ),
  ]);
  return {
    id: g.id,
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

/** A new group, led by the coordinator making it (an admin names the coordinator, or leaves it to them). */
export async function createVolunteerGroup(user: SessionUser, name: string, coordinatorId: string | null): Promise<string> {
  if (user.role !== "admin" && user.role !== "coordinator") throw new AuthError(403, "Only coordinators and admins make volunteer groups.");
  const clean = name.trim().replace(/\s+/g, " ").slice(0, 80);
  if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
  const lead = user.role === "coordinator" ? user.id : coordinatorId;
  if (lead) {
    const c = await userById(lead);
    if (!c || c.role !== "coordinator" || c.status !== "active") throw new AuthError(400, "A volunteer group is led by an active coordinator.");
  }
  const id = await tx(async (c) => {
    const conv = (await c.query<{ id: string }>("INSERT INTO conversations (kind, name, created_by) VALUES ('group', $1, $2) RETURNING id", [clean, user.id])).rows[0];
    return (await c.query<{ id: string }>("INSERT INTO volunteer_groups (name, coordinator_id, conversation_id) VALUES ($1, $2, $3) RETURNING id", [clean, lead, conv.id])).rows[0].id;
  });
  await syncVolunteerGroup(id);
  const g = (await volunteerGroup(id))!;
  await groupEvent(g.conversation_id, user.id, `${user.name || user.email} made the volunteer group "${clean}"`);
  return id;
}

/** Rename, hand to another coordinator, or open or close the chat. */
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

/**
 * Put a volunteer in a group (or in none, `toGroupId` null). Allowed for an admin; for the coordinator of the group
 * they are leaving; or, for a volunteer in no group, the coordinator of the group they join.
 */
export async function moveVolunteer(user: SessionUser, volunteerId: string, toGroupId: string | null): Promise<void> {
  const v = await one<{ id: string; name: string | null; email: string; role: string; volunteer_group_id: string | null }>(
    "SELECT id, name, email, role, volunteer_group_id FROM users WHERE id = $1",
    [volunteerId],
  );
  if (!v || v.role !== "volunteer") throw new AuthError(404, "No such volunteer.");
  const from = v.volunteer_group_id ? await volunteerGroup(v.volunteer_group_id) : undefined;
  const to = toGroupId ? await volunteerGroup(toGroupId) : undefined;
  if (toGroupId && !to) throw new AuthError(404, "No such volunteer group.");
  if (from?.id === to?.id) return;
  const allowed = user.role === "admin" || (from ? from.coordinator_id === user.id : Boolean(to && to.coordinator_id === user.id));
  if (!allowed) throw new AuthError(403, "Only an admin, or the coordinator of the group they are leaving, can move a volunteer.");
  await run("UPDATE users SET volunteer_group_id = $2, parent_id = COALESCE($3, parent_id) WHERE id = $1", [v.id, to?.id ?? null, to?.coordinator_id ?? null]);
  const name = v.name || v.email;
  if (from) {
    await syncVolunteerGroup(from.id);
    await groupEvent(from.conversation_id, user.id, `${name} moved to another group`);
  }
  if (to) {
    await syncVolunteerGroup(to.id);
    await groupEvent(to.conversation_id, user.id, `${name} joined the group`);
    const lead = to.coordinator_id ? await userById(to.coordinator_id) : undefined;
    after(() =>
      pushTo([v.id], {
        title: "Volunteer group",
        body: `You're now in ${to.name}${lead ? `, with ${lead.name || lead.email}` : ""}.`,
        link: `/chats/${to.conversation_id}`,
        tag: `vg-${to.id}`,
      }),
    );
  }
  after(() => pushSync([v.id], { scope: "chat", id: (to ?? from)!.conversation_id }));
}

/**
 * Move several of a group's volunteers at once to another group (or to none): an admin, or the group's own
 * coordinator. One line in each chat, one push per volunteer. Returns how many moved.
 */
export async function moveVolunteers(user: SessionUser, fromGroupId: string, userIds: string[], toGroupId: string | null): Promise<number> {
  const from = await volunteerGroup(fromGroupId);
  if (!from || !managesGroup(user, from)) throw new AuthError(404, "No such volunteer group.");
  const to = toGroupId ? await volunteerGroup(toGroupId) : undefined;
  if (toGroupId && !to) throw new AuthError(404, "No such volunteer group.");
  if (to && to.id === from.id) throw new AuthError(400, "They are in that group already.");
  const ids = Array.from(new Set(userIds));
  const people = ids.length
    ? await q<{ id: string; name: string | null; email: string }>(
        "SELECT id, name, email FROM users WHERE id = ANY($1::uuid[]) AND role = 'volunteer' AND volunteer_group_id = $2",
        [ids, from.id],
      )
    : [];
  if (people.length === 0) throw new AuthError(400, "Pick at least one volunteer of this group.");
  await run("UPDATE users SET volunteer_group_id = $2, parent_id = COALESCE($3, parent_id) WHERE id = ANY($1::uuid[])", [people.map((p) => p.id), to?.id ?? null, to?.coordinator_id ?? null]);
  await syncVolunteerGroup(from.id);
  if (to) await syncVolunteerGroup(to.id);
  const who = user.name || user.email;
  const names = people.length <= 3 ? people.map((p) => p.name || p.email).join(", ") : `${people.length} volunteers`;
  await groupEvent(from.conversation_id, user.id, to ? `${who} moved ${names} to ${to.name}` : `${who} took ${names} out of the group`);
  if (to) {
    await groupEvent(to.conversation_id, user.id, `${names} joined from ${from.name}`);
    const lead = to.coordinator_id ? await userById(to.coordinator_id) : undefined;
    after(() =>
      pushTo(people.map((p) => p.id), {
        title: "Volunteer group",
        body: `You're now in ${to.name}${lead ? `, with ${lead.name || lead.email}` : ""}.`,
        link: `/chats/${to.conversation_id}`,
        tag: `vg-${to.id}`,
      }),
    );
  }
  after(() => pushSync(people.map((p) => p.id), { scope: "chat", id: (to ?? from).conversation_id }));
  return people.length;
}

/** Keep a volunteer in the chat from sending, or from doing anything; or let them again. */
export async function setPermission(user: SessionUser, conversationId: string, memberId: string, permission: Permission): Promise<void> {
  const g = await volunteerGroupOfChat(conversationId);
  if (!g) throw new AuthError(400, "Only volunteer group chats have this.");
  if (!managesGroup(user, g)) throw new AuthError(403, "Only its coordinator or an admin can do that.");
  const target = await one<{ role: string; name: string | null; email: string }>(
    `SELECT u.role, u.name, u.email FROM group_members gm JOIN users u ON u.id = gm.user_id WHERE gm.conversation_id = $1 AND gm.user_id = $2`,
    [conversationId, memberId],
  );
  if (!target) throw new AuthError(404, "Not in this chat.");
  if (target.role !== "volunteer") throw new AuthError(400, "Only volunteers can be limited here.");
  await run("UPDATE group_members SET permission = $3 WHERE conversation_id = $1 AND user_id = $2", [conversationId, memberId, permission]);
  const name = target.name || target.email;
  await groupEvent(
    conversationId,
    user.id,
    permission === "full" ? `${name} can chat again` : permission === "no_messages" ? `${name} can no longer send messages` : `${name} can now only read the chat`,
  );
  after(async () => pushSync((await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1", [conversationId])).map((m) => m.user_id), { scope: "chat", id: conversationId }));
}

/** A volunteer made or promoted by a coordinator joins that coordinator's first group (made if they lead none). */
export async function placeNewVolunteer(volunteerId: string, coordinatorId: string): Promise<void> {
  const lead = await userById(coordinatorId);
  if (!lead || lead.role !== "coordinator") return;
  let g = await one<{ id: string }>("SELECT id FROM volunteer_groups WHERE coordinator_id = $1 ORDER BY created_at LIMIT 1", [coordinatorId]);
  if (!g) {
    const name = `${lead.name || lead.email}'s volunteers`;
    const id = await tx(async (c) => {
      const conv = (await c.query<{ id: string }>("INSERT INTO conversations (kind, name, created_by) VALUES ('group', $1, $2) RETURNING id", [name, lead.id])).rows[0];
      return (await c.query<{ id: string }>("INSERT INTO volunteer_groups (name, coordinator_id, conversation_id) VALUES ($1, $2, $3) RETURNING id", [name, lead.id, conv.id])).rows[0].id;
    });
    g = { id };
  }
  await run("UPDATE users SET volunteer_group_id = $2 WHERE id = $1", [volunteerId, g.id]);
  await syncVolunteerGroup(g.id);
}
