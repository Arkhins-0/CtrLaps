import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { unread } from "@/lib/messages";
import { isPushConfigured } from "@/lib/push";
import { canAnnounce, CREATE_RULES, CHANNEL_POSTERS, isDeveloper, roleLabel } from "@/lib/roles";
import { myCategories } from "@/lib/teams";
import { qrUrl, toPublic, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The signed-in person, and what they may do. */
export const GET = handle(async () => {
  const user = await requireUser();
  const parent = user.parent_id ? await userById(user.parent_id) : undefined;
  const counts = await unread(user.id);
  return json({
    user: toPublic(user),
    qrUrl: qrUrl(user),
    parent: parent ? { id: parent.id, name: parent.name || parent.email, roleLabel: roleLabel(parent.role, isDeveloper(parent)) } : null,
    canCreate: CREATE_RULES[user.role] ?? [],
    /** Sends announcements (admins and coordinators), to anyone. */
    canAnnounce: canAnnounce(user.role),
    canPostChannel: CHANNEL_POSTERS.includes(user.role),
    canRelay: user.role === "coordinator",
    canBulkEmail: user.role === "admin",
    isAdmin: user.role === "admin",
    /** A developer: answers support tickets, and may make other admins developers. */
    isDev: isDeveloper(user),
    unread: counts.total,
    unreadChats: counts.chats,
    unreadHome: counts.home,
    /** Unread support replies (and, for a developer, messages on tickets). */
    unreadSupport: counts.support,
    pushConfigured: isPushConfigured(),
    /** "My categories" this season; null = everything. */
    categoryIds: await myCategories(user),
  });
});
