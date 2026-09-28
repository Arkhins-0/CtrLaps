import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { nextRace } from "@/lib/races";
import { myCategories } from "@/lib/teams";

export const dynamic = "force-dynamic";

/** What the countdown chip shows: the live session, or the next one. */
export const GET = handle(async () => {
  const user = await requireUser();
  return json({ ...(await nextRace(await myCategories(user))), now: new Date().toISOString() });
});
