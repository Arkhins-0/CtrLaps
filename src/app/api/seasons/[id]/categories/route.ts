import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { categoriesInput, categoriesOf, saveCategories } from "@/lib/categories";
import { fail, json } from "@/lib/http";
import { seasonById } from "@/lib/seasons";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** A season's race categories, in order. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  await requireUser();
  const { id } = await params;
  if (!isUuid(id) || !(await seasonById(id))) return fail("No such season.", 404);
  return json({ categories: await categoriesOf([id]) });
});

/** Admin: the season's whole list, in its new order `{categories: [{id?, name, code, color}]}`; ones left out are removed. */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id) || !(await seasonById(id))) return fail("No such season.", 404);
  const list = categoriesInput((await body(request)).categories);
  if ("error" in list) return fail(list.error);
  await saveCategories(id, list);
  const categories = await categoriesOf([id]);
  await audit(admin.id, null, "categories.saved", { seasonId: id, categories: categories.map((c) => c.code) });
  return json({ categories });
});
