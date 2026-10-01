import { body, handle, isUuid, str, uuids, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { duplicateSend, sentBefore } from "@/lib/messages";
import { replyToTicket, setTicketStatus, ticketView } from "@/lib/support";

export const dynamic = "force-dynamic";

/** A ticket and its chat (its owner and developers). Reading marks it read, except `?read=0` (a phone in the background). */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such ticket.", 404);
  return json(await ticketView(user, id, new URL(request.url).searchParams.get("read") !== "0"));
});

/** Write in a ticket's chat while it is open `{body, fileIds, replyToId, clientId, linkUrl}`. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such ticket.", 404);
  const b = await body(request);
  const clientId = str(b.clientId, 80) || null;
  const messageId =
    (await sentBefore(user.id, clientId)) ??
    (await replyToTicket(user, id, {
      body: str(b.body, 5000),
      fileId: isUuid(str(b.fileId, 64)) ? str(b.fileId, 64) : null,
      fileIds: uuids(b.fileIds),
      linkUrl: str(b.linkUrl, 2000) || null,
      replyToId: isUuid(str(b.replyToId, 64)) ? str(b.replyToId, 64) : null,
      clientId,
    }).catch(async (error) => {
      if (duplicateSend(error)) return (await sentBefore(user.id, clientId))!;
      throw error;
    }));
  return json({ id: messageId }, 201);
});

/** Close `{status: "closed"}` (its owner or a developer) or reopen `{status: "open"}` (its owner within 2 days, a developer any time). */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such ticket.", 404);
  const status = (await body(request)).status;
  if (status !== "open" && status !== "closed") return fail("Say open or closed.");
  await setTicketStatus(user, id, status);
  return json(await ticketView(user, id));
});
