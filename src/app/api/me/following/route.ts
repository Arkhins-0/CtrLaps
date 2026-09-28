import { body, handle, uuids } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { categoriesOf } from "@/lib/categories";
import { fail, json } from "@/lib/http";
import { currentSeason } from "@/lib/seasons";
import { followedCategories, setFollowedCategories } from "@/lib/teams";

export const dynamic = "force-dynamic";

/** The race categories a user (no role yet) may follow as a fan this season, and the ones they follow. */
export const GET = handle(async () => {
  const user = await requireUser();
  const season = await currentSeason();
  const [categories, categoryIds] = await Promise.all([categoriesOf([season.id]), user.role === "user" ? followedCategories(user.id, season.id) : []]);
  return json({ categories, categoryIds, canFollow: user.role === "user" });
});

/** Follow these categories `{categoryIds}` (replaces the list). Only users: everyone else has categories by role. */
export const PUT = handle(async (request) => {
  const user = await requireUser();
  if (user.role !== "user") return fail("Your categories come with your role.", 403);
  const season = await currentSeason();
  return json({ categoryIds: await setFollowedCategories(user.id, season.id, uuids((await body(request)).categoryIds)) });
});
