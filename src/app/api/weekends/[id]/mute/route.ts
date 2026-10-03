import { body, bool, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { setMuted } from "@/lib/channels";
import { fail, json } from "@/lib/http";
import { channelFor } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** Mute or unmute this weekend channel's notifications for me `{muted}` (push only; posts still count as unread). */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const channel = await channelFor(id);
  if (!channel) return fail("No such race weekend.", 404);
  return json({ muted: await setMuted(me.id, channel.id, bool((await body(request)).muted)) });
});
