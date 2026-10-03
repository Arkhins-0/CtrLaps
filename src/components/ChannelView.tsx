"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { MessageComposer, post } from "./MessageComposer";
import { MessageItem } from "./MessageList";
import { Feed, useNewIds } from "./NewLine";
import { useHydrated } from "@/lib/useHydrated";

/** Why a channel is closed: its season is archived now, it was closed with its season, or an admin closed it. */
export type ClosedReason = "archived" | "season" | "admin" | null;

type Channel = { open: boolean; closedReason: ClosedReason; canPost: boolean };

const CLOSED_LINE: Record<Exclude<ClosedReason, null>, string> = {
  archived: "This channel is closed: its season is archived.",
  season: "This channel was closed when its season was archived.",
  admin: "This channel was closed by an admin.",
};

/**
 * A channel: a weekend's, or a race category's when [url] points at one. Admins and the channel's managers (coordinators
 * an admin picked) post; everyone reads.
 */
export function ChannelView({
  weekendId,
  initial,
  canPost,
  open,
  closedReason,
  isAdmin,
  url,
  placeholder = "Post to everyone for this weekend",
}: {
  weekendId: string;
  initial: MessageOut[];
  canPost: boolean;
  open: boolean;
  closedReason: ClosedReason;
  isAdmin: boolean;
  /** The channel's API; the weekend's by default. */
  url?: string;
  placeholder?: string;
}) {
  const base = url ?? `/api/weekends/${weekendId}/channel`;
  const router = useRouter();
  const [messages, setMessages] = useState(initial);
  const newIds = useNewIds(initial);
  const hydrated = useHydrated();
  const [channel, setChannel] = useState<Channel>({ open, closedReason, canPost });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // The page re-renders after the weekend menu opens or closes the channel: take its word.
  useEffect(() => setChannel({ open, closedReason, canPost }), [open, closedReason, canPost]);

  const reload = async () => {
    try {
      const r = await api<{ messages: MessageOut[] } & Channel>(base);
      setMessages(r.messages);
      setChannel({ open: r.open, closedReason: r.closedReason, canPost: r.canPost });
    } catch {
      // Next time.
    }
  };

  const openChannel = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(base, { method: "PATCH", json: { open: true } });
      await reload();
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not open the channel.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    const timer = setInterval(reload, 20_000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [base]);

  return (
    <div className="space-y-3">
      {channel.canPost && (
        <MessageComposer
          placeholder={placeholder}
          submitLabel="Post"
          send={async (draft) => {
            await post(base, draft);
            await reload();
          }}
        />
      )}
      {!channel.open && (
        <div className="flex items-center gap-3">
          <p className="min-w-0 flex-1 text-xs text-snow-faint">{CLOSED_LINE[channel.closedReason ?? "admin"]}</p>
          {/* A channel in an archived season opens only once the season is brought back. */}
          {isAdmin && channel.closedReason !== "archived" && (
            <button className="btn-ghost shrink-0 px-3 py-1 text-xs" onClick={openChannel} disabled={busy}>
              {busy ? "Opening…" : "Open channel"}
            </button>
          )}
        </div>
      )}
      {error && <p className="error">{error}</p>}
      {messages.length === 0 && <p className="card text-sm text-snow-faint">No posts yet.</p>}
      <Feed list={[...messages].reverse()} newIds={newIds} hydrated={hydrated} render={(m) => <MessageItem m={m} inPlace timeOnly />} />
    </div>
  );
}
