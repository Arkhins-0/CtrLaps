import { body, handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isPushKind, pushSettings, setPushSetting } from "@/lib/pushPrefs";

export const dynamic = "force-dynamic";

/** Which notifications you hear about: `{ kinds: [{ key, label, hint, on }] }`. */
export const GET = handle(async () => {
  const user = await requireUser();
  return json({ kinds: await pushSettings(user.id) });
});

/** Switch one kind on or off: `{ kind, enabled }`. */
export const PUT = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  if (!isPushKind(b.kind) || typeof b.enabled !== "boolean") return fail("Choose a kind and on or off.");
  await setPushSetting(user.id, b.kind, b.enabled);
  return json({ kinds: await pushSettings(user.id) });
});
