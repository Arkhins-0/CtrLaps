import { body, handle, isUuid, str, strings, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";
import { deleteFaq, updateFaq } from "@/lib/support";

export const dynamic = "force-dynamic";

/** A developer changes a question: any of `{category, question, answer, steps, position}`. */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  if (!isDeveloper(user)) return fail("Only a developer can change the FAQs.", 403);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such question.", 404);
  const b = await body(request);
  const text = (v: unknown, max: number) => (typeof v === "string" ? str(v, max) || undefined : undefined);
  const faq = await updateFaq(id, {
    category: text(b.category, 80),
    question: text(b.question, 300),
    answer: text(b.answer, 4000),
    steps: Array.isArray(b.steps) ? strings(b.steps, 30).map((s) => s.trim().slice(0, 500)).filter(Boolean) : undefined,
    position: typeof b.position === "number" && Number.isInteger(b.position) ? b.position : undefined,
  });
  if (!faq) return fail("No such question.", 404);
  return json({ faq });
});

export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  if (!isDeveloper(user)) return fail("Only a developer can change the FAQs.", 403);
  const { id } = await params;
  if (!isUuid(id) || !(await deleteFaq(id))) return fail("No such question.", 404);
  return json({ ok: true });
});
