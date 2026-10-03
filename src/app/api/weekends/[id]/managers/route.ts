import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { channelManagers, managerCandidates, setChannelManagers } from "@/lib/channels";
import { fail, json } from "@/lib/http";
import { weekendById } from "@/lib/races";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Who manages this weekend's channel; for an admin, also who may be picked (active coordinators). */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  return json({ managers: await channelManagers(id), candidates: me.role === "admin" ? await managerCandidates() : [] });
});

/** Admin: the coordinators who manage this weekend's channel, exactly `userIds`. */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id) || !(await weekendById(id))) return fail("No such race weekend.", 404);
  const b = await body(request);
  const ids = Array.isArray(b.userIds) ? b.userIds.filter((x): x is string => typeof x === "string" && isUuid(x)) : [];
  const managers = await setChannelManagers(admin, id, ids);
  await audit(admin.id, id, "weekend.managers", { userIds: ids });
  return json({ managers });
});
