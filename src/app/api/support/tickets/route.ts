import { body, handle, str, uuids } from "@/lib/api";
import { currentUser, recordAttempt, requireUser, tooManyAttempts } from "@/lib/auth";
import { clientAddress, fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";
import { listTickets, raiseTicket } from "@/lib/support";
import { isSupportCategory } from "@/lib/supportCategories";

export const dynamic = "force-dynamic";

/** Your tickets — or, for a developer, everyone's — `?status=open|closed|all` (all by default). */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const s = new URL(request.url).searchParams.get("status");
  const status = s === "open" || s === "closed" ? s : "all";
  return json({ tickets: await listTickets(user, status), isDev: isDeveloper(user) });
});

/**
 * Raise a ticket `{name, email, phone, category, subject, details, fileIds}`. Signed in or not (someone who can't sign
 * in needs help too); without an account it is answered by email, and is theirs once they sign in with that email.
 */
export const POST = handle(async (request) => {
  const user = await currentUser();
  const b = await body(request);
  // A box people never see: a bot fills it in.
  if (str(b.website, 200)) return json({ id: null, number: 0, label: "" }, 201);
  const email = str(b.email, 200).toLowerCase();
  const d = {
    name: str(b.name, 120),
    email,
    phone: str(b.phone, 40) || null,
    category: str(b.category, 80),
    subject: str(b.subject, 200),
    details: str(b.details, 5000),
    fileIds: user ? uuids(b.fileIds, 10) : [],
  };
  if (d.name.length < 2) return fail("Enter your name.");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail("Enter a valid email address.");
  if (!isSupportCategory(d.category)) return fail("Choose what it is about.");
  if (d.subject.length < 3) return fail("Write a short subject.");
  if (d.details.length < 10) return fail("Tell us a little more in the details.");
  // Someone not signed in is held to ten tickets per quarter hour, by email and by address (kept apart from sign-in attempts).
  if (!user) {
    const key = `support:${email}`;
    const ip = `support:${clientAddress(request)}`;
    if (await tooManyAttempts(key, ip)) return fail("Too many requests. Try again in a few minutes.", 429);
    await recordAttempt(key, ip);
  }
  return json(await raiseTicket(user, d), 201);
});
