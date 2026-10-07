"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "./Icon";

/*
 * Short messages at the bottom of the screen ("Saved", "Couldn't send"), as the app's snackbars. `toast("Saved")` or
 * `toast({ text, tone, action })` from any client component; <Toasts /> is mounted once, in the signed-in frame
 * (Shell). Without it mounted (a signed-out page), a toast simply isn't shown.
 */

export type ToastTone = "info" | "success" | "error";
export type ToastAction = { label: string; onClick: () => void };
export type ToastInput = string | { text: string; tone?: ToastTone; action?: ToastAction; duration?: number };

type Item = { id: number; text: string; tone: ToastTone; action?: ToastAction; duration: number };

const SHOWN = 4;
const DURATION_MS = 4_500;
// Long enough to read the message and still reach the button.
const WITH_ACTION_MS = 8_000;

const listeners = new Set<(item: Item) => void>();
let seq = 0;

/** Show a message bar. A plain string is an info message. */
export function toast(input: ToastInput): void {
  const t = typeof input === "string" ? { text: input } : input;
  const item: Item = {
    id: ++seq,
    text: t.text,
    tone: t.tone ?? "info",
    action: t.action,
    duration: t.duration ?? (t.action ? WITH_ACTION_MS : DURATION_MS),
  };
  listeners.forEach((l) => l(item));
}

const STRIPE: Record<ToastTone, string> = { info: "bg-snow-faint", success: "bg-gold", error: "bg-danger" };

/** Where the messages show: full width above the tab bar on a phone, bottom right on a laptop; the newest lowest. */
export function Toasts() {
  const [items, setItems] = useState<Item[]>([]);
  useEffect(() => {
    // Past four, the oldest goes: a burst of messages never covers the page.
    const add = (item: Item) => setItems((xs) => [...xs, item].slice(-SHOWN));
    listeners.add(add);
    return () => {
      listeners.delete(add);
    };
  }, []);
  const close = (id: number) => setItems((xs) => xs.filter((x) => x.id !== id));
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-[calc(5.25rem+env(safe-area-inset-bottom))] z-[60] flex flex-col gap-2 px-4 lg:inset-x-auto lg:bottom-6 lg:right-6 lg:w-96 lg:px-0">
      {items.map((t) => (
        <ToastBar key={t.id} t={t} onClose={() => close(t.id)} />
      ))}
    </div>
  );
}

function ToastBar({ t, onClose }: { t: Item; onClose: () => void }) {
  const box = useRef<HTMLDivElement>(null);
  const [paused, setPaused] = useState(false);
  const left = useRef(t.duration);
  const close = useRef(onClose);
  close.current = onClose;

  // The clock stops while the pointer or focus is on it (reading it, reaching for Undo) and carries on after.
  useEffect(() => {
    if (paused) return;
    const started = Date.now();
    const timer = window.setTimeout(() => close.current(), left.current);
    return () => {
      window.clearTimeout(timer);
      left.current -= Date.now() - started;
    };
  }, [paused]);

  // A click closes it, but not one that ends selecting its text: that text is there to be copied.
  const clicked = () => {
    const selection = window.getSelection();
    if (selection && !selection.isCollapsed && box.current?.contains(selection.anchorNode)) return;
    onClose();
  };

  return (
    <div
      ref={box}
      role={t.tone === "error" ? "alert" : "status"}
      className="toast-in pointer-events-auto flex cursor-pointer select-text items-stretch overflow-hidden rounded-2xl border border-night-line bg-night-panel shadow-card"
      onClick={clicked}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <span className={`w-1 shrink-0 ${STRIPE[t.tone]}`} aria-hidden />
      <p className="min-w-0 flex-1 break-words px-4 py-3 text-sm text-snow">{t.text}</p>
      {t.action && (
        <button
          type="button"
          className="shrink-0 px-3 text-sm font-bold text-gold transition-colors hover:bg-gold/10"
          onClick={(e) => {
            e.stopPropagation();
            t.action?.onClick();
            onClose();
          }}
        >
          {t.action.label}
        </button>
      )}
      {/* For the keyboard: the bar itself isn't focusable. */}
      <button
        type="button"
        className="grid w-10 shrink-0 place-items-center text-snow-faint transition-colors hover:text-snow"
        aria-label="Dismiss"
        onClick={(e) => {
          e.stopPropagation();
          onClose();
        }}
      >
        <Icon name="close" className="h-4 w-4" />
      </button>
    </div>
  );
}
