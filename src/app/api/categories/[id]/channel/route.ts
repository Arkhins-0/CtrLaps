import { body, bool, handle, isUuid, str, uuids, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { canPostCategory, categoryConversation, categoryInfo, setCategoryChannelOpen } from "@/lib/categoryChannels";
import { isMuted } from "@/lib/channels";
import { fail, json } from "@/lib/http";
import { canReadCategory, conversationMessages, markConversationRead, postToCategory } from "@/lib/messages";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** A race category's channel, for everyone. Reading marks it read, except `?read=0` (the phone in the background). */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such category.", 404);
  const category = await categoryInfo(id);
  if (!category || !(await canReadCategory(user, id))) return fail("No such category.", 404);
  const conversationId = await categoryConversation(id);
  const messages = await conversationMessages(user, conversationId);
  if (new URL(request.url).searchParams.get("read") !== "0") await markConversationRead(user.id, conversationId);
  return json({
    channelId: conversationId,
    category: { id: category.id, name: category.name, code: category.code, color: category.color, seasonName: category.seasonName },
    open: category.open,
    closedReason: category.closedReason,
    canPost: category.open && (await canPostCategory(user, id)),
    muted: await isMuted(user.id, conversationId),
    messages,
  });
});

/** Admin: close or reopen the channel (`{ open: true | false }`). A channel in an archived season stays closed. */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such category.", 404);
  const category = await categoryInfo(id);
  if (!category) return fail("No such category.", 404);
  const open = bool((await body(request)).open);
  if (open && category.closedReason === "archived") return fail("Its season is archived. Bring the season back first.");
  await setCategoryChannelOpen(id, open);
  await audit(admin.id, null, open ? "category.channel_opened" : "category.channel_closed", { categoryId: id, name: category.name });
  return json({ open: (await categoryInfo(id))?.open ?? open });
});

/** Admins and the channel's managers post; it reaches everyone (who may mute it). */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such category.", 404);
  const b = await body(request);
  const messageId = await postToCategory(user, id, {
    body: str(b.body, 5000),
    linkUrl: str(b.linkUrl, 2000) || null,
    fileId: str(b.fileId, 64) || null,
    fileIds: uuids(b.fileIds),
    urgent: bool(b.urgent),
  });
  return json({ id: messageId }, 201);
});
