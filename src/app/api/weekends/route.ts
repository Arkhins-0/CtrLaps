import { body, handle } from "@/lib/api";
import { categoriesOf } from "@/lib/categories";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { createWeekend, listWeekends, weekendById } from "@/lib/races";
import { currentSeason } from "@/lib/seasons";
import { audit } from "@/lib/users";
import { weekendInput } from "@/lib/weekendInput";

export const dynamic = "force-dynamic";

export const GET = handle(async (request) => {
  await requireUser();
  const season = new URL(request.url).searchParams.get("season") || undefined;
  const weekends = await listWeekends(season);
  // The categories of every season shown (and of the current one, for a new weekend).
  const seasonIds = [...weekends.map((w) => w.seasonId ?? ""), season ?? (await currentSeason()).id];
  return json({ weekends, categories: await categoriesOf(seasonIds) });
});

/** Admin: a new race weekend. Sessions are added to it afterwards. */
export const POST = handle(async (request) => {
  const admin = await requireUser(["admin"]);
  const input = weekendInput(await body(request));
  if ("error" in input) return fail(input.error);
  const id = await createWeekend(input);
  await audit(admin.id, id, "weekend.created", input);
  return json({ weekend: await weekendById(id) }, 201);
});
