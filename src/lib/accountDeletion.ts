import "server-only";

import { AuthError, revokeAll, type SessionUser } from "./auth";
import { one, q, tx } from "./db";
import { sendNotice } from "./email";
import { SITE_URL } from "./config";
import { randomToken, verifyCode } from "./ids";
import { storage } from "./storage";
import { audit } from "./users";

/*
 * Deleting an account. The person asks (Account → Delete my account), or a developer does it for them (a request by
 * email); either way it waits GRACE_DAYS — they are signed out everywhere, and signing in again cancels it — then
 * runDueDeletions (daily) erases it:
 *
 * - erased: name, email, phone, date of birth, photo, team, password, sessions and phone links, email choices and
 *   follows, votes and event replies, receipts, group memberships, and support tickets with their chat;
 * - kept, from "Deleted user": what they sent in private chats and groups (the other people's conversation stays
 *   whole), and announcements and channel posts they made as an organiser; race results keep the typed name, as in
 *   any published results, but lose the link to the account;
 * - the account row stays as an empty "Deleted user" (status `deleted`), so other people's conversations keep their
 *   shape; its codes are replaced, so its old QR and account code no longer work. The audit log keeps only "an
 *   account was deleted".
 */

export const GRACE_DAYS = 7;

/** The last active admin can't go: someone has to run the place. */
async function lastAdmin(user: { id: string; role: string }): Promise<boolean> {
  if (user.role !== "admin") return false;
  const other = await one("SELECT 1 FROM users WHERE role = 'admin' AND status = 'active' AND deletion_due_at IS NULL AND id <> $1 LIMIT 1", [user.id]);
  return !other;
}

/** Start the wait: signed out everywhere, an email with the date and how to cancel. */
export async function requestDeletion(target: SessionUser, by: SessionUser): Promise<{ dueAt: string }> {
  if (target.status === "deleted") throw new AuthError(400, "This account is already deleted.");
  if (await lastAdmin(target)) throw new AuthError(403, "The last admin can't be deleted. Make someone else an admin first.");
  const row = await one<{ due: string }>(
    `UPDATE users SET deletion_due_at = now() + ($2 || ' days')::interval, deletion_requested_by = $3 WHERE id = $1 RETURNING deletion_due_at AS due`,
    [target.id, String(GRACE_DAYS), by.id],
  );
  await revokeAll(target.id);
  await audit(by.id, target.id, "account.deletion_requested", { self: by.id === target.id });
  const due = new Date(row!.due);
  const when = due.toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
  await sendNotice(
    [{ email: target.email, name: target.name }],
    "Your account will be deleted",
    "Your account will be deleted",
    `${by.id === target.id ? "You asked" : "We were asked"} to delete your CTR[L]APS account and the details we hold about you.\n\nIt will be deleted on ${when}. Until then you are signed out. If you change your mind, just sign in again before that date and the deletion is cancelled.`,
    `${SITE_URL}/`,
    [],
    { eyebrow: "Account" },
  ).catch((error) => console.error("[deletion] request mail", error));
  return { dueAt: due.toISOString() };
}

/** Stop the wait: signing in again, or a developer. */
export async function cancelDeletion(userId: string, by: string | null): Promise<boolean> {
  const row = await one<{ email: string; name: string | null }>(
    "UPDATE users SET deletion_due_at = NULL, deletion_requested_by = NULL WHERE id = $1 AND deletion_due_at IS NOT NULL AND status <> 'deleted' RETURNING email, name",
    [userId],
  );
  if (!row) return false;
  await audit(by, userId, "account.deletion_cancelled");
  await sendNotice(
    [{ email: row.email, name: row.name }],
    "Your account won't be deleted",
    "Deletion cancelled",
    "Your CTR[L]APS account will not be deleted: you signed in again, or the request was withdrawn. Everything stays as it was.",
    `${SITE_URL}/`,
    [],
    { eyebrow: "Account" },
  ).catch((error) => console.error("[deletion] cancel mail", error));
  return true;
}

/** Erase one account now (see above). */
export async function eraseAccount(userId: string): Promise<void> {
  const user = await one<{ email: string; name: string | null; photo_key: string | null; status: string }>(
    "SELECT email, name, photo_key, status FROM users WHERE id = $1",
    [userId],
  );
  if (!user || user.status === "deleted") return;
  const email = user.email.toLowerCase();

  const files = await tx(async (c) => {
    // Their support tickets, chat and all (the conversation takes the ticket and its messages with it).
    await c.query(
      `DELETE FROM conversations WHERE id IN (SELECT conversation_id FROM support_tickets WHERE user_id = $1 OR lower(email) = $2)`,
      [userId, email],
    );
    // Everything else that is about them.
    for (const table of ["sessions", "push_tokens", "auth_tokens", "email_preferences", "result_subscriptions", "category_followers", "category_people", "poll_votes", "event_replies", "group_members", "group_invites", "channel_managers", "message_recipients"]) {
      await c.query(`DELETE FROM ${table} WHERE user_id = $1`, [userId]);
    }
    await c.query("DELETE FROM login_attempts WHERE lower(email) = $1", [email]);
    await c.query("DELETE FROM signups WHERE lower(email) = $1", [email]);
    // Results stay (the typed name, as published); the link to the account goes.
    await c.query("UPDATE session_results SET user_id = NULL WHERE user_id = $1", [userId]);
    // Files they uploaded that nothing uses any more.
    const unused = (
      await c.query<{ id: string; key: string }>(
        `SELECT f.id, f.key FROM files f WHERE f.uploaded_by = $1
           AND NOT EXISTS (SELECT 1 FROM message_files mf WHERE mf.file_id = f.id)
           AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.file_id = f.id)`,
        [userId],
      )
    ).rows;
    if (unused.length) await c.query("DELETE FROM files WHERE id = ANY($1::uuid[])", [unused.map((f) => f.id)]);
    // The account itself: an empty "Deleted user" that can never sign in, with new codes so the old QR is dead.
    await c.query(
      `UPDATE users SET name = 'Deleted user', email = $2, phone = NULL, dob = NULL, photo_key = NULL, team_name = NULL, team_id = NULL,
              password_hash = NULL, status = 'deleted', is_dev = false, verify_code = $3, qr_token = $4, mail_token = gen_random_uuid(),
              deleted_at = now(), deletion_due_at = NULL
       WHERE id = $1`,
      [userId, `deleted-${userId}@deleted.invalid`, `DEL${verifyCode().slice(3)}`, randomToken()],
    );
    // The log keeps only that an account was deleted, and when.
    await c.query("DELETE FROM audit_log WHERE target_id = $1", [userId]);
    await c.query("INSERT INTO audit_log (actor_id, target_id, action) VALUES (NULL, $1, 'account.deleted')", [userId]);
    return unused;
  });

  // The bytes, once the database no longer points at them.
  if (user.photo_key) await storage().remove(user.photo_key).catch((error) => console.error("[deletion] photo", error));
  for (const f of files) await storage().remove(f.key).catch((error) => console.error("[deletion] file", error));

  await sendNotice(
    [{ email: user.email, name: user.name }],
    "Your account has been deleted",
    "Your account has been deleted",
    "Your CTR[L]APS account and the details we held about you have been deleted. Race results that were published stay, as they are part of the championship record. Thank you for using CTR[L]APS.",
    undefined,
    [],
    { eyebrow: "Account" },
  ).catch((error) => console.error("[deletion] done mail", error));
}

/** Erase every account whose wait is over. Run daily (the cron route), and safe to run any time. */
export async function runDueDeletions(): Promise<number> {
  const due = await q<{ id: string }>("SELECT id FROM users WHERE deletion_due_at IS NOT NULL AND deletion_due_at <= now() AND status <> 'deleted'");
  for (const u of due) await eraseAccount(u.id);
  return due.length;
}

/** Whether this account is waiting to be deleted, and when. */
export async function deletionDue(userId: string): Promise<string | null> {
  const row = await one<{ due: string | null }>("SELECT deletion_due_at AS due FROM users WHERE id = $1", [userId]);
  return row?.due ? new Date(row.due).toISOString() : null;
}
