import { handle, isUuid, str } from "@/lib/api";
import { isEmailKind, unsubscribe } from "@/lib/emailPrefs";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/**
 * "Stop these emails" from a mail's footer, without signing in: `{u, k}` (the account's mail key, the kind), as JSON
 * or a form. Only a POST changes anything, so a link scanner opening the page can't unsubscribe anyone.
 */
export const POST = handle(async (request) => {
  const type = request.headers.get("content-type") ?? "";
  const data = type.includes("application/json")
    ? ((await request.json().catch(() => ({}))) as Record<string, unknown>)
    : Object.fromEntries((await request.formData().catch(() => new FormData())).entries());
  const u = str(data.u, 64);
  const k = str(data.k, 40);
  if (!isUuid(u) || !isEmailKind(k)) return fail("That link isn't valid.", 400);
  const done = await unsubscribe(u, k);
  if (!done) return fail("That link isn't valid any more.", 404);
  return json({ ok: true });
});
