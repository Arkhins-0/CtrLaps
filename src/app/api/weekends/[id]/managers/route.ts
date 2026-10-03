import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser, USER_COLUMNS, type SessionUser } from "@/lib/auth";
import { q } from "@/lib/db";
import { channelManagers, setChannelManagers } from "@/lib/channels";
import { fail, json } from "@/lib/http";
import { weekendById } from "@/lib/races";
import { audit, toPublic } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Who manages this weekend's channel; for admins and coordinators, also who may be picked (active admins and coordinators). */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const picks = me.role === "admin" || me.role === "coordinator";
  const candidates = picks
    ? (await q<SessionUser>(`SELECT ${USER_COLUMNS} FROM users WHERE status = 'active' AND role IN ('admin', 'coordinator') ORDER BY name NULLS LAST, email`)).map(toPublic)
    : [];
  return json({ managers: await channelManagers(id), candidates });
});

/** Admins and coordinators: set the managers to exactly `userIds`. */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin", "coordinator"]);
  const { id } = await params;
  if (!isUuid(id) || !(await weekendById(id))) return fail("No such race weekend.", 404);
  const b = await body(request);
  const ids = Array.isArray(b.userIds) ? b.userIds.filter((x): x is string => typeof x === "string" && isUuid(x)) : [];
  const managers = await setChannelManagers(admin, id, ids);
  await audit(admin.id, id, "weekend.managers", { userIds: ids });
  return json({ managers });
});
