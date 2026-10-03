"use client";

import { useState } from "react";
import { api } from "@/lib/client";

/** Mute or unmute a channel's notifications for me (push only; unread still counts). `url`: the channel's mute API. */
export function MuteButton({ url, initial }: { url: string; initial: boolean }) {
  const [muted, setMuted] = useState(initial);
  const [busy, setBusy] = useState(false);
  const toggle = async () => {
    setBusy(true);
    try {
      const r = await api<{ muted: boolean }>(url, { method: "PUT", json: { muted: !muted } });
      setMuted(r.muted);
    } catch {
      // Left as it was; the next tap tries again.
    } finally {
      setBusy(false);
    }
  };
  return (
    <button
      className={`chip shrink-0 gap-1.5 ${muted ? "border-gold/60 text-gold" : "hover:border-snow/40"}`}
      onClick={toggle}
      disabled={busy}
      aria-pressed={muted}
      title={muted ? "Notifications are off for this channel. Tap to turn them on." : "Turn off notifications for this channel"}
    >
      <span aria-hidden>{muted ? "🔕" : "🔔"}</span>
      {muted ? "Muted" : "Notifications on"}
    </button>
  );
}
