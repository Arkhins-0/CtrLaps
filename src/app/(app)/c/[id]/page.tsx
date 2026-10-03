import { notFound } from "next/navigation";
import { MuteButton } from "@/components/channels/MuteButton";
import { isMuted } from "@/lib/channels";
import { ChannelView } from "@/components/ChannelView";
import { CategoryTag } from "@/components/schedule/CategoryTag";
import { canPostCategory, categoryConversation, categoryInfo } from "@/lib/categoryChannels";
import { canReadCategory, conversationMessages, markConversationRead } from "@/lib/messages";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Category channel" };

/** A race category's channel ("ITC 2026"): its people read, admins, coordinators and its race officials post. */
export default async function CategoryChannelPage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const category = await categoryInfo(id);
  if (!category || !(await canReadCategory(user, id))) notFound();
  const conversationId = await categoryConversation(id);
  const [messages, mayPost] = await Promise.all([conversationMessages(user, conversationId), canPostCategory(user, id)]);
  await markConversationRead(user.id, conversationId);

  return (
    <div className="space-y-5">
      <div className="card flex items-center gap-3">
        <CategoryTag category={category} />
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-semibold">{category.name}</h1>
          <p className="text-xs text-snow-faint">Category channel · {category.seasonName}</p>
        </div>
        <MuteButton url={`/api/categories/${id}/mute`} initial={await isMuted(user.id, conversationId)} />
      </div>
      <ChannelView
        weekendId=""
        url={`/api/categories/${id}/channel`}
        placeholder={`Post to everyone in the ${category.code} channel`}
        initial={messages}
        canPost={category.open && mayPost}
        open={category.open}
        closedReason={category.open ? null : "archived"}
        isAdmin={false}
      />
    </div>
  );
}
