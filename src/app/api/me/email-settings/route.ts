import { body, handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { emailSettings, saveEmailSettings } from "@/lib/emailPrefs";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Your email choices: each kind on or off, and which race categories' results come (yours to start). */
export const GET = handle(async () => json(await emailSettings(await requireUser())));

/** Change them `{kinds: {announcements: false, …}, results: {<categoryId>: true, …}}`; anything left out stays as it is. */
export const PUT = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const kinds = b.kinds && typeof b.kinds === "object" ? (b.kinds as Record<string, boolean>) : {};
  const results = b.results && typeof b.results === "object" ? (b.results as Record<string, boolean>) : {};
  await saveEmailSettings(user, kinds, results);
  return json(await emailSettings(user));
});
