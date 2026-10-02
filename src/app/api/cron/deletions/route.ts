import { handle } from "@/lib/api";
import { runDueDeletions } from "@/lib/accountDeletion";
import { env } from "@/lib/env";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Daily (vercel.json crons): erase the accounts whose 7-day wait is over. Vercel sends `Bearer <CRON_SECRET>`. */
export const GET = handle(async (request) => {
  if (!env.cronSecret) return fail("Not set up.", 503);
  if (request.headers.get("authorization") !== `Bearer ${env.cronSecret}`) return fail("Not allowed.", 401);
  return json({ erased: await runDueDeletions() });
});
