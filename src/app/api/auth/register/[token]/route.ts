import { cookies } from "next/headers";
import { body, handle, str, type Params } from "@/lib/api";
import {
  consumeSignup,
  createSession,
  hashPassword,
  passwordProblem,
  SESSION_COOKIE,
  sessionCookieOptions,
  signupEmail,
  USER_COLUMNS,
  type SessionUser,
} from "@/lib/auth";
import { one } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { TERMS_VERSION } from "@/lib/legal";
import { audit, createUser, toPublic, userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

const GONE = "This link is no longer valid. Register again to get a new one.";

/** The email a registration link confirms, for the page to show. */
export const GET = handle<Params<"token">>(async (_request, { params }) => {
  const { token } = await params;
  const email = await signupEmail(token);
  if (!email) return fail(GONE, 404);
  return json({ email });
});

/** The email is confirmed: make the account (role 'user'), set its password and sign the person in. */
export const POST = handle<Params<"token">>(async (request, { params }) => {
  const { token } = await params;
  const email = await signupEmail(token);
  if (!email) return fail(GONE, 404);
  if (await userByEmail(email)) {
    await consumeSignup(token);
    return fail("This email already has an account. Sign in, or choose a new password from “Forgot password”.", 409);
  }

  const b = await body(request);
  if (b.acceptTerms !== true) return fail("Agree to the Terms and Conditions and the Privacy Policy to continue.");
  const password = str(b.password, 200);
  const problem = passwordProblem(password);
  if (problem) return fail(problem);
  const platform = b.platform === "android" ? "android" : "web";

  const created = await createUser({ email, role: "user", parentId: null, createdBy: null });
  const user = await one<SessionUser>(
    `UPDATE users SET password_hash = $2, status = 'active', terms_accepted_at = now(), terms_version = $3
     WHERE id = $1 RETURNING ${USER_COLUMNS}`,
    [created.id, await hashPassword(password), TERMS_VERSION],
  );
  await consumeSignup(token);
  await audit(created.id, created.id, "user.registered", { termsVersion: TERMS_VERSION });

  const session = await createSession(created.id, platform);
  if (platform === "web") (await cookies()).set(SESSION_COOKIE, session, sessionCookieOptions());
  return json({ token: platform === "android" ? session : undefined, user: toPublic(user!) });
});
