"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";

/**
 * A sheet, as the app's: on a phone it slides up from the bottom, on a laptop it sits in the middle. A title, what it
 * holds, and Close (or, for a question, the `footer` the caller gives: see ConfirmSheet). Escape or a tap outside
 * closes it.
 */
export function Sheet({
  title,
  onClose,
  children,
  wide = false,
  footer,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  wide?: boolean;
  footer?: React.ReactNode;
}) {
  useEffect(() => {
    const key = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", key);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", key);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);
  // Drawn at the page's root, and clicks stop here: a sheet opened from inside a link (a photo in a chat row) never
  // follows that link.
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 backdrop-blur-sm sm:items-center sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        onClose();
      }}
    >
      <div
        className={`flex max-h-[92dvh] w-full flex-col rounded-t-[28px] border border-night-line bg-night pb-[env(safe-area-inset-bottom)] shadow-2xl sm:rounded-[28px] ${wide ? "sm:max-w-2xl" : "sm:max-w-md"}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto mt-2.5 h-1 w-10 rounded-full bg-snow-faint/50 sm:hidden" />
        <h2 className="px-5 pb-3 pt-4 text-center text-lg font-bold">{title}</h2>
        <div className="flat min-h-0 flex-1 overflow-y-auto px-5">{children}</div>
        <div className="p-4">
          {footer ?? (
            <button type="button" className="btn w-full border border-gold/60 py-3 text-gold hover:bg-gold/10" onClick={onClose} autoFocus>
              Close
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}

/*
 * "Don't ask me again": a question the person turned off is remembered in this browser under `ctrlaps:dont-ask:<key>`.
 * Storage can be missing or blocked (a private window); then the question is simply asked every time.
 */
const DONT_ASK = "ctrlaps:dont-ask:";

/** True when the person ticked "Don't ask me again" on this question before: the caller goes ahead without the sheet. */
export function askedBefore(key: string): boolean {
  try {
    return localStorage.getItem(DONT_ASK + key) === "1";
  } catch {
    return false;
  }
}

function rememberAnswer(key: string) {
  try {
    localStorage.setItem(DONT_ASK + key, "1");
  } catch {}
}

/** The questions turned off in this browser (their keys), for Settings to offer to bring them back. */
export function questionsTurnedOff(): string[] {
  try {
    return Object.keys(localStorage).filter((k) => k.startsWith(DONT_ASK));
  } catch {
    return [];
  }
}

/** Asks every turned-off question again. */
export function askAllAgain() {
  for (const k of questionsTurnedOff()) {
    try {
      localStorage.removeItem(k);
    } catch {}
  }
}

/**
 * A question as a sheet: what it means, then Cancel and the button that goes ahead (gold, or red for something that
 * ends or removes something, like signing out). With `dontAskKey`, a "Don't ask me again" box above them; it only counts when
 * the person goes ahead, so ticking it and then cancelling changes nothing. The caller checks `askedBefore(key)` first.
 */
export function ConfirmSheet({
  title,
  children,
  confirmLabel,
  danger = false,
  busy = false,
  dontAskKey,
  onConfirm,
  onClose,
}: {
  title: string;
  children?: React.ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  dontAskKey?: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const [dontAsk, setDontAsk] = useState(false);
  const go = () => {
    if (dontAskKey && dontAsk) rememberAnswer(dontAskKey);
    onConfirm();
  };
  return (
    <Sheet
      title={title}
      onClose={() => !busy && onClose()}
      footer={
        <div className="space-y-4">
          {dontAskKey && (
            <label className="flex cursor-pointer items-center justify-center gap-2.5 text-sm text-snow-soft">
              <input type="checkbox" className="h-4 w-4 accent-gold" checked={dontAsk} onChange={(e) => setDontAsk(e.target.checked)} />
              Don&apos;t ask me again
            </label>
          )}
          <div className="flex gap-3">
            {/* For a red question, focus starts on the safe answer: Enter alone never goes ahead. */}
            <button type="button" className="btn-ghost flex-1 py-3" onClick={onClose} disabled={busy} autoFocus={danger}>
              Cancel
            </button>
            <button type="button" className={`${danger ? "btn-danger" : "btn-gold"} flex-1 py-3`} onClick={go} disabled={busy} autoFocus={!danger}>
              {busy ? "Working…" : confirmLabel}
            </button>
          </div>
        </div>
      }
    >
      {children}
    </Sheet>
  );
}
