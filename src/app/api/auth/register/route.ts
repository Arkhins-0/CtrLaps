import { body, handle, str } from "@/lib/api";
import { issueSignup, recordAttempt, tooManyAttempts } from "@/lib/auth";
import { sendAlreadyRegistered, sendSignup } from "@/lib/email";
import { clientAddress, fail, json } from "@/lib/http";
import { userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Start registering: email a link that confirms the address. The account is
 * made only when that link is opened. Always answers the same, so the form
 * cannot be used to find out who has an account.
 */
export const POST = handle(async (request) => {
  const b = await body(request);
  const email = str(b.email, 200).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail("Enter a valid email address.");
  const ip = clientAddress(request);
  if (await tooManyAttempts(email, ip)) return json({ ok: true });
  await recordAttempt(email, ip);
  const existing = await userByEmail(email);
  if (existing) {
    await sendAlreadyRegistered(email).catch((error) => console.error("[register]", error));
  } else {
    const token = await issueSignup(email, 24);
    await sendSignup(email, token).catch((error) => console.error("[register]", error));
  }
  return json({ ok: true });
});
