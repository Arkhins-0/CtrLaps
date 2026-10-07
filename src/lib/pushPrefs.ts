import "server-only";

import { q, run } from "./db";

/*
 * Which notifications a person hears about. Each kind is on until they switch it off; urgent messages (schedule
 * changes, urgent announcements) come whatever is chosen, so nobody misses a safety notice. Messages still arrive
 * in the app either way: only the popup is held back.
 */

export const PUSH_KINDS = ["chats", "announcements", "channels", "results"] as const;
export type PushKind = (typeof PUSH_KINDS)[number];
export const isPushKind = (v: unknown): v is PushKind => typeof v === "string" && (PUSH_KINDS as readonly string[]).includes(v);

export const PUSH_KIND_LABEL: Record<PushKind, { label: string; hint: string }> = {
  chats: { label: "Chats", hint: "Private messages, groups and support replies" },
  announcements: { label: "Announcements", hint: "From the organisers; urgent ones always come" },
  channels: { label: "Channel posts", hint: "Race weekend and category channels" },
  results: { label: "Results", hint: "When a session's results are in" },
};

/** The kind a push belongs to, from what the app's popup says it is; null for those that always come (support, invites). */
export function pushKindOf(popupKind: string | undefined, explicit?: PushKind): PushKind | null {
  if (explicit) return explicit;
  switch (popupKind) {
    case "chat":
    case "group":
      return "chats";
    case "announcement":
      return "announcements";
    case "channel":
      return "channels";
    default:
      return null;
  }
}

/** Of these people, those who keep `kind` on. */
export async function wantsPush(userIds: string[], kind: PushKind): Promise<string[]> {
  if (userIds.length === 0) return [];
  const off = new Set(
    (await q<{ user_id: string }>("SELECT user_id FROM push_preferences WHERE user_id = ANY($1::uuid[]) AND kind = $2 AND NOT enabled", [userIds, kind])).map(
      (r) => r.user_id,
    ),
  );
  return userIds.filter((id) => !off.has(id));
}

export type PushSetting = { key: PushKind; label: string; hint: string; on: boolean };

export async function pushSettings(userId: string): Promise<PushSetting[]> {
  const off = new Set(
    (await q<{ kind: string }>("SELECT kind FROM push_preferences WHERE user_id = $1 AND NOT enabled", [userId])).map((r) => r.kind),
  );
  return PUSH_KINDS.map((key) => ({ key, ...PUSH_KIND_LABEL[key], on: !off.has(key) }));
}

export async function setPushSetting(userId: string, kind: PushKind, enabled: boolean): Promise<void> {
  await run(
    `INSERT INTO push_preferences (user_id, kind, enabled) VALUES ($1, $2, $3)
     ON CONFLICT (user_id, kind) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now()`,
    [userId, kind, enabled],
  );
}
