"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import { Skeleton } from "../Skeleton";
import { toast } from "../Toasts";

type Kind = { key: string; label: string; hint: string; on: boolean };

/** The switch, as Theme's: the accent when on. */
function Switch({ on, onChange, label, disabled }: { on: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!on)}
      className={`relative h-8 w-14 shrink-0 rounded-full border transition-colors disabled:opacity-60 ${on ? "border-gold bg-gold" : "border-snow-faint bg-night-high"}`}
    >
      <span className={`absolute top-1 rounded-full transition-all ${on ? "left-7 bg-ink" : "left-1 bg-snow-faint"}`} style={{ width: 22, height: 22 }} />
    </button>
  );
}

/**
 * A switch per kind of notification (chats, announcements, channel posts, results), each saved as it's flipped. The
 * switch moves at once and goes back if the save fails.
 */
export function NotificationSettings() {
  const [kinds, setKinds] = useState<Kind[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);

  useEffect(() => {
    api<{ kinds: Kind[] }>("/api/me/push-preferences")
      .then((r) => setKinds(r.kinds))
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load your notification choices."));
  }, []);

  const flip = async (key: string, enabled: boolean) => {
    setSaving(key);
    setKinds((ks) => ks?.map((k) => (k.key === key ? { ...k, on: enabled } : k)) ?? ks);
    try {
      const r = await api<{ kinds: Kind[] }>("/api/me/push-preferences", { method: "PUT", json: { kind: key, enabled } });
      setKinds(r.kinds);
    } catch (e) {
      setKinds((ks) => ks?.map((k) => (k.key === key ? { ...k, on: !enabled } : k)) ?? ks);
      toast({ text: e instanceof Error ? e.message : "Could not save. Try again.", tone: "error" });
    } finally {
      setSaving(null);
    }
  };

  return (
    <div>
      <p className="px-2 pb-2 text-sm text-snow-soft">Urgent messages always come. These are for your phone&apos;s popups; everything still arrives in the app.</p>
      {error && <p className="error mx-2">{error}</p>}
      {!kinds &&
        !error &&
        [0, 1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-5 px-2 py-3.5" aria-hidden>
            <span className="min-w-0 flex-1 space-y-2">
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-3 w-2/3" />
            </span>
            <Skeleton className="h-8 w-14 rounded-full" />
          </div>
        ))}
      {kinds?.map((k) => (
        <div key={k.key} className="flex items-center gap-5 px-2 py-3.5">
          <span className="min-w-0 flex-1">
            <span className="block font-bold">{k.label}</span>
            <span className="block text-sm text-snow-soft">{k.hint}</span>
          </span>
          <Switch on={k.on} onChange={(v) => flip(k.key, v)} label={k.label} disabled={saving === k.key} />
        </div>
      ))}
    </div>
  );
}
