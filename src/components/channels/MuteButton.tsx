"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import { BellIcon } from "./BellIcon";

/**
 * Mute or unmute a channel's notifications for me (push only; unread still counts): a bell, struck through when
 * muted. It flips at once and a toast says so while the request runs behind it; if the server refuses, it flips back.
 */
export function MuteButton({ url, initial }: { url: string; initial: boolean }) {
  const [muted, setMuted] = useState(initial);
  const [toast, setToast] = useState<string | null>(null);
  const timer = useRef<number>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  const say = (text: string) => {
    setToast(text);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setToast(null), 2_500);
  };
  const toggle = async () => {
    const next = !muted;
    setMuted(next);
    say(next ? "Notifications muted" : "Notifications on");
    try {
      const r = await api<{ muted: boolean }>(url, { method: "PUT", json: { muted: next } });
      setMuted(r.muted);
    } catch {
      setMuted(!next);
      say("Couldn't change notifications. Try again.");
    }
  };
  return (
    <>
      <button
        className={`btn-icon shrink-0 ${muted ? "text-gold" : "text-snow-soft hover:text-snow"}`}
        onClick={toggle}
        aria-pressed={muted}
        aria-label={muted ? "Muted. Turn notifications on" : "Notifications on. Mute this channel"}
        title={muted ? "Muted" : "Notifications on"}
      >
        <BellIcon muted={muted} />
      </button>
      {toast && (
        <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex justify-center" role="status" aria-live="polite">
          <span className="rounded-full border border-night-line bg-night-panel px-4 py-2 text-sm text-snow shadow-card">{toast}</span>
        </div>
      )}
    </>
  );
}
