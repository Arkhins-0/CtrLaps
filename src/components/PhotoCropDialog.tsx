"use client";

import { useEffect, useState } from "react";
import Cropper, { type Area } from "react-easy-crop";

const OUT = 900;

/** The chosen square of the picture as a JPEG, no larger than 900 px a side. */
async function cropToFile(src: string, area: Area): Promise<File> {
  const img = new Image();
  img.src = src;
  await img.decode();
  const size = Math.min(OUT, Math.round(area.width));
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  canvas.getContext("2d")!.drawImage(img, area.x, area.y, area.width, area.height, 0, 0, size, size);
  const blob = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Could not crop the photo."))), "image/jpeg", 0.9),
  );
  return new File([blob], "photo.jpg", { type: "image/jpeg" });
}

/** Crop a chosen photo to a square before it is used: drag to move, pinch, scroll or slide to zoom. */
export function PhotoCropDialog({ file, onCancel, onDone }: { file: File; onCancel: () => void; onDone: (cropped: File) => void }) {
  const [src, setSrc] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<Area | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const url = URL.createObjectURL(file);
    setSrc(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const done = async () => {
    if (!src || !area) return;
    setBusy(true);
    try {
      onDone(await cropToFile(src, area));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not crop the photo.");
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black/90" role="dialog" aria-modal="true" aria-label="Crop photo">
      <div className="flex items-center justify-between px-4 py-3">
        <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <span className="text-sm font-semibold">Crop photo</span>
        <button type="button" className="btn-gold px-4 py-1.5 text-xs" onClick={done} disabled={busy || !area}>
          {busy ? "…" : "Done"}
        </button>
      </div>
      <div className="relative flex-1">
        {src && (
          <Cropper
            image={src}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="round"
            showGrid={false}
            maxZoom={5}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={(_, pixels) => setArea(pixels)}
          />
        )}
      </div>
      <div className="mx-auto flex w-full max-w-sm items-center gap-3 px-6 py-4">
        <span className="text-xs text-snow-faint">Zoom</span>
        <input type="range" min={1} max={5} step={0.01} value={zoom} onChange={(e) => setZoom(Number(e.target.value))} className="flex-1 accent-gold" aria-label="Zoom" />
      </div>
      {error && <p className="error mx-4 mb-4">{error}</p>}
    </div>
  );
}
