"use client";

/* eslint-disable @next/next/no-img-element */
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import { Avatar } from "./Avatar";
import { PhotoCropDialog } from "./PhotoCropDialog";

/** A round photo; tapping it picks a new one, cropped square first. */
export function EditablePhoto({
  src,
  name,
  size,
  disabled,
  onPicked,
}: {
  src: string | null;
  name: string;
  size: number;
  disabled?: boolean;
  onPicked: (photo: File) => void;
}) {
  const [cropping, setCropping] = useState<File | null>(null);
  return (
    <>
      {cropping && <PhotoCropDialog file={cropping} onCancel={() => setCropping(null)} onDone={(f) => { setCropping(null); onPicked(f); }} />}
      <label className={`relative shrink-0 ${disabled ? "pointer-events-none opacity-60" : "cursor-pointer"}`} title="Change photo" style={{ width: size, height: size }}>
        <Avatar src={src} name={name} size={size} preview={false} />
        <input
          type="file"
          accept="image/*"
          className="hidden"
          disabled={disabled}
          onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ""; if (f) setCropping(f); }}
        />
      </label>
    </>
  );
}

/** A person's photo at the top of their page: their manager or an admin taps it to change it, saved at once. */
export function PersonPhoto({ personId, src, name, size }: { personId: string; src: string | null; name: string; size: number }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const save = async (photo: File) => {
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.set("photo", await shrinkImage(photo), "photo.jpg");
      await api(`/api/users/${personId}/photo`, { method: "POST", body: form });
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change the photo.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="shrink-0">
      <EditablePhoto src={src} name={name} size={size} disabled={busy} onPicked={save} />
      {error && <p className="error mt-2 max-w-[12rem] text-xs">{error}</p>}
    </div>
  );
}

/** A picked photo shown before it is saved. */
export function usePreview(file: File | null): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!file) return setUrl(null);
    const u = URL.createObjectURL(file);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [file]);
  return url;
}
