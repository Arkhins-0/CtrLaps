import { Shell } from "@/components/Shell";
import { unread } from "@/lib/messages";
import { hasChats, isDeveloper, roleLabel } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { userPhotoUrl } from "@/lib/profile";

export const dynamic = "force-dynamic";

/** Every signed-in page: the person must be active and set up. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const user = await requireProfile();
  const counts = await unread(user.id);
  return (
    <Shell
      user={{ name: user.name || user.email, roleLabel: roleLabel(user.role, isDeveloper(user)), photoUrl: userPhotoUrl(user.id, user.photo_key), hasChats: hasChats(user.role) }}
      unreadHome={counts.home}
      unreadChats={counts.chats}
    >
      {children}
    </Shell>
  );
}
