"use client";
/* eslint-disable @next/next/no-img-element */

import { usePhotoPullUp, type PhotoEdit } from "./PhotoPullUp";

export type { PhotoEdit };

/**
 * A profile photo, or initials. A click on a photo pulls it up large (as in the app), with Change and Remove when
 * `edit` is given; a click on initials picks a first photo with `edit`. Without it, initials say "No photo yet" only
 * where the photo stands alone (`sayNone`: a banner); in a list row they leave the click to the row, as in the app.
 * `preview={false}` where a click already does something (the tab bar's photo, a photo in a picker).
 */
export function Avatar({
  src,
  name,
  size = 40,
  preview = true,
  edit,
  sayNone = false,
}: {
  src: string | null;
  name: string;
  size?: number;
  preview?: boolean;
  edit?: PhotoEdit | null;
  sayNone?: boolean;
}) {
  const photo = usePhotoPullUp({ src, name, edit });
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
  const face = src ? (
    <img src={src} alt="" width={size} height={size} className="shrink-0 rounded-full border border-night-line object-cover" style={{ width: size, height: size }} />
  ) : (
    <div
      className="flex shrink-0 items-center justify-center rounded-full bg-gold/15 font-semibold text-gold"
      style={{ width: size, height: size, fontSize: size / 2.6 }}
      aria-hidden
    >
      {initials || "?"}
    </div>
  );
  if (!preview || (!src && !edit && !sayNone)) return face;
  const open = (e: React.SyntheticEvent) => {
    // Inside a link or a row, the click is the photo's, not the row's.
    e.preventDefault();
    e.stopPropagation();
    photo.tap();
  };
  return (
    <>
      {/* Not a <button>: it often sits inside a link or a button (a chat row). */}
      <span
        role="button"
        tabIndex={0}
        className={`shrink-0 rounded-full transition-opacity ${photo.busy ? "opacity-60" : src ? "cursor-zoom-in" : edit ? "cursor-pointer" : ""}`}
        aria-label={src ? `${name}'s photo` : edit ? `Add a photo for ${name}` : `${name}: no photo yet`}
        aria-busy={photo.busy || undefined}
        onClick={open}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") open(e);
        }}
      >
        {face}
      </span>
      {photo.ui}
    </>
  );
}
