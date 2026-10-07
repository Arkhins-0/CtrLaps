"use client";

import { useEffect } from "react";
import { createPortal } from "react-dom";

/**
 * A sheet, as the app's: on a phone it slides up from the bottom, on a laptop it sits in the middle. A title, what it
 * holds, and Close. Escape or a tap outside closes it.
 */
export function Sheet({ title, onClose, children, wide = false }: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
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
          <button type="button" className="btn w-full border border-gold/60 py-3 text-gold hover:bg-gold/10" onClick={onClose} autoFocus>
            Close
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
