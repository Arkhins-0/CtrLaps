import { body, handle, str } from "@/lib/api";
import { issueToken, requireUser } from "@/lib/auth";
import { sendEmailChange } from "@/lib/email";
import { fail, json } from "@/lib/http";
import { editsOwnProfile } from "@/lib/roles";
import { userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Someone with no role yet, or an admin, asks to change their email: a link goes to the new address, and nothing changes until it is opened. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  if (!editsOwnProfile(user.role)) return fail("Your profile is locked. Ask your manager or an admin to change it.", 403);
  const b = await body(request);
  const email = str(b.email, 200).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail("Enter a valid email address.");
  if (email === user.email) return fail("That is already your email.");
  if (await userByEmail(email)) return fail("That email already has an account.", 409);
  const token = await issueToken(user.id, "email", 24, email);
  await sendEmailChange(email, token);
  return json({ ok: true });
});
