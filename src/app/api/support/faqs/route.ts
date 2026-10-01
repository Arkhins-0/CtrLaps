import { body, handle, str, strings } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";
import { addFaq, listFaqs } from "@/lib/support";

export const dynamic = "force-dynamic";

/** The FAQs, in order. Open to anyone, signed in or not (the sign-in screen's "Need help?"). */
export const GET = handle(async () => json({ faqs: await listFaqs() }));

/** A developer adds a question `{category, question, answer, steps}`; it goes last. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  if (!isDeveloper(user)) return fail("Only a developer can change the FAQs.", 403);
  const b = await body(request);
  const d = {
    category: str(b.category, 80),
    question: str(b.question, 300),
    answer: str(b.answer, 4000),
    steps: strings(b.steps, 30).map((s) => s.trim().slice(0, 500)).filter(Boolean),
  };
  if (!d.category || !d.question || !d.answer) return fail("A category, a question and an answer are needed.");
  return json({ faq: await addFaq(d) }, 201);
});
