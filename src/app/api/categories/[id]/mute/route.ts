import { body, bool, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { categoryConversation, categoryInfo } from "@/lib/categoryChannels";
import { setMuted } from "@/lib/channels";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Mute or unmute this category channel's notifications for me `{muted}` (push only; posts still count as unread). */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id) || !(await categoryInfo(id))) return fail("No such category.", 404);
  return json({ muted: await setMuted(me.id, await categoryConversation(id), bool((await body(request)).muted)) });
});
