import { handle, type Params } from "@/lib/api";
import { consumeToken, emailChange } from "@/lib/auth";
import { run } from "@/lib/db";
import { sendEmailChanged } from "@/lib/email";
import { fail, json } from "@/lib/http";
import { editsOwnProfile } from "@/lib/roles";
import { audit, userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

const GONE = "This link is no longer valid. Ask for a new one from your account page.";

/** What the confirm page shows: the address the account moves to. */
export const GET = handle<Params<"token">>(async (_request, { params }) => {
  const { token } = await params;
  const change = await emailChange(token);
  if (!change) return fail(GONE, 404);
  return json({ email: change.newEmail });
});

/** The new address is confirmed: the account takes it, and the old address is told. */
export const POST = handle<Params<"token">>(async (_request, { params }) => {
  const { token } = await params;
  const change = await emailChange(token);
  if (!change) return fail(GONE, 404);
  if (!editsOwnProfile(change.user.role)) {
    await consumeToken(token);
    return fail("Your profile is locked now. Ask your manager or an admin to change your email.", 403);
  }
  if (await userByEmail(change.newEmail)) {
    await consumeToken(token);
    return fail("That email already has an account.", 409);
  }
  await run("UPDATE users SET email = $2 WHERE id = $1", [change.user.id, change.newEmail]);
  await consumeToken(token);
  await audit(change.user.id, change.user.id, "email.changed", { from: change.user.email, to: change.newEmail });
  await sendEmailChanged(change.user.email, change.newEmail).catch((error) => console.error("[email change]", error));
  return json({ email: change.newEmail });
});
