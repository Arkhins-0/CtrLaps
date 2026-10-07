"use client";
/* eslint-disable @next/next/no-img-element */

import { useState } from "react";
import { Sheet } from "./Sheet";

/**
 * A profile photo, or initials. A click on a photo pulls it up large (as in the app); `preview={false}` where a click
 * already does something (the tab bar's photo, a photo being changed).
 */
export function Avatar({ src, name, size = 40, preview = true }: { src: string | null; name: string; size?: number; preview?: boolean }) {
  const [open, setOpen] = useState(false);
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
  if (!src) {
    return (
      <div
        className="flex shrink-0 items-center justify-center rounded-full border border-night-line bg-night text-snow-soft"
        style={{ width: size, height: size, fontSize: size / 2.6 }}
        aria-hidden
      >
        {initials || "?"}
      </div>
    );
  }
  const img = (
    <img src={src} alt="" width={size} height={size} className="shrink-0 rounded-full border border-night-line object-cover" style={{ width: size, height: size }} />
  );
  if (!preview) return img;
  return (
    <>
      {/* Not a <button>: it often sits inside a link or a button (a chat row). */}
      <span
        role="button"
        tabIndex={0}
        className="shrink-0 cursor-zoom-in rounded-full"
        aria-label={`${name}'s photo`}
        onClick={(e) => {
          // Inside a link or a row, the click is the photo's, not the row's.
          e.preventDefault();
          e.stopPropagation();
          setOpen(true);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            e.stopPropagation();
            setOpen(true);
          }
        }}
      >
        {img}
      </span>
      {open && (
        <Sheet title={name} onClose={() => setOpen(false)}>
          <img src={src} alt={`${name}'s photo`} className="aspect-square w-full rounded-3xl object-cover" />
        </Sheet>
      )}
    </>
  );
}
