"use client";

/* eslint-disable @next/next/no-img-element */
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import { Avatar, type PhotoEdit } from "./Avatar";
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

/** A photo saved at once to `url` (POST to change it, DELETE to take it away), then the page is drawn again. */
function useSavedPhoto(url: string): PhotoEdit {
  const router = useRouter();
  return {
    upload: async (photo) => {
      const form = new FormData();
      form.set("photo", await shrinkImage(photo), "photo.jpg");
      await api(url, { method: "POST", body: form });
      router.refresh();
    },
    remove: async () => {
      await api(url, { method: "DELETE" });
      router.refresh();
    },
  };
}

/** A team's photo at the top of its page: an admin, a coordinator or the team's manager taps it to see, change or remove it. */
export function TeamPhoto({ teamId, src, name, size }: { teamId: string; src: string | null; name: string; size: number }) {
  const edit = useSavedPhoto(`/api/teams/${teamId}/photo`);
  return <Avatar src={src} name={name} size={size} edit={edit} />;
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
