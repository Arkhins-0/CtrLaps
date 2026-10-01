"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";

/** For a developer on another admin's page: make them a developer (they answer support), or take it back. */
export function DeveloperToggle({ personId, isDev }: { personId: string; isDev: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const change = async () => {
    if (isDev && !confirm("Take back developer access? They stay an admin, and no longer see support tickets.")) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/api/users/${personId}/developer`, { method: "PUT", json: { dev: !isDev } });
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change it.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card space-y-2">
      <h2 className="font-semibold">Developer</h2>
      <p className="text-xs text-snow-faint">
        {isDev
          ? "A developer: an admin who also answers support tickets. Shown as Developer here, and only as Support to people who ask for help."
          : "Make this admin a developer: they also answer support tickets, and show as Developer."}
      </p>
      {error && <p className="error">{error}</p>}
      <button className={isDev ? "btn-ghost" : "btn-gold"} disabled={busy} onClick={change}>
        {busy ? "Saving…" : isDev ? "Remove developer" : "Make developer"}
      </button>
    </section>
  );
}
