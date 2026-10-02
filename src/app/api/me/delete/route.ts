import { cookies } from "next/headers";
import { body, handle, str } from "@/lib/api";
import { GRACE_DAYS, requestDeletion } from "@/lib/accountDeletion";
import { requireUser, SESSION_COOKIE, sessionCookieOptions, verifyPassword } from "@/lib/auth";
import { one } from "@/lib/db";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/**
 * Delete my account `{password, confirm: "DELETE"}`: signed out everywhere at once, erased after GRACE_DAYS unless
 * they sign in again before then (see accountDeletion.ts).
 */
export const POST = handle(async (request) => {
  const me = await requireUser();
  const b = await body(request);
  if (str(b.confirm, 20).trim().toUpperCase() !== "DELETE") return fail("Type DELETE to confirm.");
  const stored = await one<{ password_hash: string | null }>("SELECT password_hash FROM users WHERE id = $1", [me.id]);
  if (!(await verifyPassword(str(b.password, 200), stored?.password_hash ?? null))) return fail("Wrong password.", 401);
  const { dueAt } = await requestDeletion(me, me);
  (await cookies()).set(SESSION_COOKIE, "", sessionCookieOptions(new Date(0)));
  return json({ ok: true, dueAt, graceDays: GRACE_DAYS });
});
