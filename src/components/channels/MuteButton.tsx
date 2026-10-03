"use client";

import { useState } from "react";
import { api } from "@/lib/client";
import { BellIcon } from "./BellIcon";

/** Mute or unmute a channel's notifications for me (push only; unread still counts): a bell, struck through when muted. */
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
      className={`btn-icon shrink-0 ${muted ? "text-gold" : "text-snow-soft hover:text-snow"}`}
      onClick={toggle}
      disabled={busy}
      aria-pressed={muted}
      aria-label={muted ? "Muted. Turn notifications on" : "Notifications on. Mute this channel"}
      title={muted ? "Muted" : "Notifications on"}
    >
      <BellIcon muted={muted} />
    </button>
  );
}
