"use client";
/* eslint-disable @next/next/no-img-element */

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "./Icon";
import { PhotoCropDialog } from "./PhotoCropDialog";
import { Sheet } from "./Sheet";

/** What those who may change a photo can do with it; the caller saves to the server and refreshes. */
export type PhotoEdit = {
  /** Save a picked photo: already cropped square, or as picked for a wide photo. Throws to show what went wrong. */
  upload: (photo: File) => Promise<void>;
  remove: () => Promise<void>;
};

/**
 * How every photo behaves when tapped, as in the app: a photo pulls up large, with Change and Remove under it for
 * those who may change it; where there is no photo yet, those who may add one go straight to picking it, and
 * everyone else is told there's none. `wide` is a 16:9 photo (a weekend's track), shown wide and not cropped square.
 *
 * Returns what a tap does (`tap`) and what to draw beside the photo (`ui`: the sheet, the cropper, the toast); `ui`
 * goes beside the tapped element, not inside it, so its keys and clicks don't reach it.
 */
export function usePhotoPullUp({ src, name, edit, wide = false }: { src: string | null; name: string; edit?: PhotoEdit | null; wide?: boolean }) {
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [cropping, setCropping] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const timer = useRef<number>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const say = (text: string) => {
    setToast(text);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setToast(null), 3_000);
  };

  const close = () => {
    setOpen(false);
    setConfirming(false);
    setError(null);
  };

  // With the sheet closed (a first photo picked from the initials), a failure is said in the toast instead.
  const failed = (e: unknown, fallback: string) => {
    const message = e instanceof Error ? e.message : fallback;
    if (open) setError(message);
    else say(message);
  };

  const save = async (photo: File) => {
    if (!edit) return;
    setBusy(true);
    setError(null);
    try {
      await edit.upload(photo);
      close();
    } catch (e) {
      failed(e, "Could not change the photo.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!edit) return;
    setBusy(true);
    setError(null);
    try {
      await edit.remove();
      close();
    } catch (e) {
      failed(e, "Could not remove the photo.");
    } finally {
      setBusy(false);
    }
  };

  // A file input made for the moment, never put in the page: a click on one inside a link (a chat row) would
  // follow the link.
  const pick = () => {
    const input = document.createElement("input");
    input.type = "file";
    // A wide photo goes up as picked, so only what the server takes; a round one is cropped to a JPEG first.
    input.accept = wide ? "image/jpeg,image/png,image/webp" : "image/*";
    input.onchange = () => {
      const f = input.files?.[0];
      if (!f) return;
      if (wide) save(f);
      else setCropping(f);
    };
    input.click();
  };

  const tap = () => {
    if (busy) return;
    if (src) {
      setError(null);
      setConfirming(false);
      setOpen(true);
    } else if (edit) pick();
    else say("No photo yet");
  };

  const ui = (
    <>
      {/* The sheet steps aside while the picked photo is cropped, and comes back saying "Saving…". */}
      {open && src && !cropping && (
        <Sheet title={name} onClose={close} wide={wide}>
          <img src={src} alt={wide ? name : `${name}'s photo`} className={`w-full rounded-3xl object-cover ${wide ? "aspect-video" : "aspect-square"}`} />
          {error && <p className="error mt-3">{error}</p>}
          {edit &&
            (confirming ? (
              <div className="space-y-3 py-4 text-center">
                <p className="font-semibold">Remove the photo?</p>
                <div className="flex justify-center gap-2">
                  <button type="button" className="btn-ghost" onClick={() => setConfirming(false)} disabled={busy}>
                    Cancel
                  </button>
                  <button type="button" className="btn-danger" onClick={remove} disabled={busy}>
                    {busy ? "Removing…" : "Remove"}
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex justify-center gap-2 py-4">
                <button type="button" className="group flex w-20 flex-col items-center gap-1.5 disabled:opacity-50" onClick={pick} disabled={busy}>
                  <span className="grid h-14 w-14 place-items-center rounded-full bg-gold/15 text-gold transition-colors group-hover:bg-gold/25">
                    <Icon name="edit" className="h-6 w-6" />
                  </span>
                  <span className="text-xs font-semibold text-snow-soft">{busy ? "Saving…" : "Change"}</span>
                </button>
                <button type="button" className="group flex w-20 flex-col items-center gap-1.5 disabled:opacity-50" onClick={() => setConfirming(true)} disabled={busy}>
                  <span className="grid h-14 w-14 place-items-center rounded-full bg-danger/15 text-danger transition-colors group-hover:bg-danger/25">
                    <Icon name="trash" className="h-6 w-6" />
                  </span>
                  <span className="text-xs font-semibold text-danger">Remove</span>
                </button>
              </div>
            ))}
        </Sheet>
      )}
      {/* Drawn at the page's root, and its clicks stop there, as the sheet's: never a link's they happen to sit in. */}
      {cropping &&
        createPortal(
          <div onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
            <PhotoCropDialog
              file={cropping}
              onCancel={() => setCropping(null)}
              onDone={(f) => {
                setCropping(null);
                save(f);
              }}
            />
          </div>,
          document.body,
        )}
      {toast &&
        createPortal(
          <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex justify-center px-4" role="status" aria-live="polite">
            <span className="rounded-full border border-night-line bg-night-panel px-4 py-2 text-sm text-snow shadow-card">{toast}</span>
          </div>,
          document.body,
        )}
    </>
  );

  return { tap, busy, ui };
}
