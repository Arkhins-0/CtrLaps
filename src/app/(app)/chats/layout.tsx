import { Suspense } from "react";
import { ChatList } from "@/components/ChatList";
import { ChatsSkeleton } from "@/components/chat/ChatsSkeleton";
import { myConversations } from "@/lib/messages";
import { hasChats } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { listVolunteerGroups } from "@/lib/volunteers";

/**
 * Chats are two panes on a laptop: the list on the left, the open
 * conversation on the right. On a phone the list and a conversation are
 * separate screens. While the list loads, its outline shows (a loading.tsx
 * here would only cover the pane, not the list).
 */
export default function ChatsLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={<ChatsSkeleton />}>
      <Chats>{children}</Chats>
    </Suspense>
  );
}

async function Chats({ children }: { children: React.ReactNode }) {
  const user = await requireProfile();
  const conversations = (await myConversations(user)).filter((c) => c.lastMessageAt);
  // Admins and coordinators get the Volunteers and Delegations pages; a volunteer or a delegate gets their one group
  // pinned above their chats.
  const staff = user.role === "admin" || user.role === "coordinator";
  const pinned = user.role === "volunteer" ? await listVolunteerGroups(user, "volunteer") : user.role === "race_official" ? await listVolunteerGroups(user, "delegation") : [];
  return (
    <ChatList conversations={conversations} canOpen={hasChats(user.role)} staffGroups={staff} pinned={pinned}>
      {children}
    </ChatList>
  );
}
