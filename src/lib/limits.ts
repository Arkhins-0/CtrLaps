import "server-only";
import { one } from "./db";
import { AuthError } from "./auth";

/**
 * The same post from the same person moments ago (a second tap, or a send tried again after its answer was lost):
 * its id, so the caller answers with it and nobody is told twice. Same place (a channel, or for announcements the
 * very same people), same words, same first file, not deleted, within a minute.
 */
export async function samePostJustNow(
  senderId: string,
  where: { conversationId: string } | { recipientIds: string[] },
  body: string,
  firstFileId: string | null,
): Promise<string | null> {
  const row =
    "conversationId" in where
      ? await one<{ id: string }>(
          `SELECT id FROM messages WHERE sender_id = $1 AND conversation_id = $2 AND body = $3 AND file_id IS NOT DISTINCT FROM $4
             AND deleted_at IS NULL AND created_at > now() - interval '1 minute' ORDER BY created_at DESC LIMIT 1`,
          [senderId, where.conversationId, body, firstFileId],
        )
      : await one<{ id: string }>(
          `SELECT m.id FROM messages m WHERE m.sender_id = $1 AND m.conversation_id IS NULL AND m.body = $2 AND m.file_id IS NOT DISTINCT FROM $3
             AND m.deleted_at IS NULL AND m.created_at > now() - interval '1 minute'
             AND (SELECT array_agg(r.user_id ORDER BY r.user_id) FROM message_recipients r WHERE r.message_id = m.id) = $4::uuid[]
           ORDER BY m.created_at DESC LIMIT 1`,
          [senderId, body, firstFileId, [...where.recipientIds].sort()],
        );
  return row?.id ?? null;
}
