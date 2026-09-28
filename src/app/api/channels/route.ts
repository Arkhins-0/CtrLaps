import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { listCategoryChannels } from "@/lib/categoryChannels";
import { listChannels } from "@/lib/channels";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Every race weekend's channel, season by season, the current season first; and the race categories' channels this person is in. */
export const GET = handle(async () => {
  const user = await requireUser();
  const [seasons, categories] = await Promise.all([listChannels(user), listCategoryChannels(user)]);
  return json({ seasons, categories });
});
