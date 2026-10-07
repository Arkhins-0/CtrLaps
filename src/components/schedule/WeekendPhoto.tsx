"use client";

import { useRouter } from "next/navigation";
import { api } from "@/lib/client";
import { usePhotoPullUp, type PhotoEdit } from "../PhotoPullUp";

/**
 * A weekend's photo (the track) across the top of its page, fading into the page below. Tapped, it pulls up large;
 * admins change or remove it there, and add the first one from "Add a track photo". With no photo, everyone else
 * sees nothing.
 */
export function WeekendPhoto({ weekendId, photoUrl, isAdmin }: { weekendId: string; photoUrl: string | null; isAdmin: boolean }) {
  const router = useRouter();
  const url = `/api/weekends/${weekendId}/photo`;
  const edit: PhotoEdit | null = isAdmin
    ? {
        // Sent as picked: a wide photo isn't cropped square, and the server takes it up to 5 MB.
        upload: async (photo) => {
          const form = new FormData();
          form.append("photo", photo);
          await api(url, { method: "POST", body: form });
          router.refresh();
        },
        remove: async () => {
          await api(url, { method: "DELETE" });
          router.refresh();
        },
      }
    : null;
  const photo = usePhotoPullUp({ src: photoUrl, name: "Track photo", edit, wide: true });
  if (!photoUrl && !isAdmin) return null;

  if (!photoUrl) {
    return (
      <div>
        <button type="button" className="btn-ghost px-3 py-1 text-xs" disabled={photo.busy} onClick={photo.tap}>
          {photo.busy ? "Uploading…" : "Add a track photo"}
        </button>
        {photo.ui}
      </div>
    );
  }
  return (
    <>
      <button
        type="button"
        className={`relative block h-40 w-full cursor-zoom-in overflow-hidden rounded-2xl transition-opacity sm:h-56 ${photo.busy ? "opacity-60" : ""}`}
        aria-label="Track photo"
        onClick={photo.tap}
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- a signed-in, versioned image from our own API */}
        <img src={photoUrl} alt="" className="h-full w-full object-cover" />
        <span className="absolute inset-0 bg-gradient-to-t from-night via-night/30 to-transparent" />
      </button>
      {photo.ui}
    </>
  );
}
