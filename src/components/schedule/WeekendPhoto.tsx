"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { api } from "@/lib/client";

/**
 * A weekend's photo (the track) across the top of its page, fading into the page below. Admins add, change or remove
 * it here; with no photo, everyone else sees nothing.
 */
export function WeekendPhoto({ weekendId, photoUrl, isAdmin }: { weekendId: string; photoUrl: string | null; isAdmin: boolean }) {
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!photoUrl && !isAdmin) return null;

  const upload = async (file: File) => {
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("photo", file);
      await api(`/api/weekends/${weekendId}/photo`, { method: "POST", body: form });
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not upload the photo.");
    } finally {
      setBusy(false);
    }
  };
  const remove = async () => {
    if (!confirm("Remove this weekend's photo?")) return;
    setBusy(true);
    try {
      await api(`/api/weekends/${weekendId}/photo`, { method: "DELETE" });
      router.refresh();
    } finally {
      setBusy(false);
    }
  };

  const controls = isAdmin && (
    <div className="flex gap-2">
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          e.target.value = "";
          if (f) upload(f);
        }}
      />
      <button type="button" className="btn-ghost bg-night/70 px-3 py-1 text-xs backdrop-blur" disabled={busy} onClick={() => input.current?.click()}>
        {busy ? "Uploading…" : photoUrl ? "Change photo" : "Add a track photo"}
      </button>
      {photoUrl && (
        <button type="button" className="btn-ghost bg-night/70 px-3 py-1 text-xs text-danger backdrop-blur" disabled={busy} onClick={remove}>
          Remove
        </button>
      )}
    </div>
  );

  if (!photoUrl) {
    return (
      <div className="space-y-2">
        {controls}
        {error && <p className="error">{error}</p>}
      </div>
    );
  }
  return (
    <div className="space-y-2">
      <div className="relative h-40 overflow-hidden rounded-2xl sm:h-56">
        {/* eslint-disable-next-line @next/next/no-img-element -- a signed-in, versioned image from our own API */}
        <img src={photoUrl} alt="" className="h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-night via-night/30 to-transparent" />
        {controls && <div className="absolute right-3 top-3">{controls}</div>}
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
