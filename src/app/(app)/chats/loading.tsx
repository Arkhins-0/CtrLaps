import { ChatPaneSkeleton } from "@/components/chat/ChatsSkeleton";

/** Beside the chat list while a conversation loads (the list itself stays). */
export default function ChatsLoading() {
  return <ChatPaneSkeleton />;
}
