"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";

type Settings = {
  automatic: boolean;
  kinds: { key: string; label: string; hint: string; on: boolean }[];
  results: { id: string; code: string; name: string; color: string; on: boolean; mine: boolean }[];
};

/**
 * Which emails this account gets: a switch per kind, and the race categories whose results come (your own to start).
 * Each change saves at once. Account and security mails, and the organisers' Email page, always come.
 */
export function EmailSettings() {
  const [s, setS] = useState<Settings | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Settings>("/api/me/email-settings").then(setS).catch((e) => setError(e instanceof Error ? e.message : "Could not load."));
  }, []);

  const save = async (patch: { kinds?: Record<string, boolean>; results?: Record<string, boolean> }) => {
    setError(null);
    try {
      setS(await api<Settings>("/api/me/email-settings", { method: "PUT", json: patch }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
    }
  };

  const resultsOn = s?.kinds.find((k) => k.key === "results")?.on ?? false;
  return (
    <section id="email" className="card space-y-4">
      <div>
        <h2 className="font-semibold">Email</h2>
        <p className="text-xs text-snow-faint">Choose what comes by email. Everything still shows in the app.</p>
      </div>
      {error && <p className="error">{error}</p>}
      {!s && !error && <p className="text-sm text-snow-faint">Loading…</p>}
      {s && !s.automatic && <p className="text-sm text-snow-soft">Your role gets no automatic email: your coordinator forwards what matters.</p>}
      {s?.automatic && (
        <>
          <ul className="divide-y divide-night-line">
            {s.kinds.map((k) => (
              <li key={k.key} className="py-2.5">
                <label className="flex cursor-pointer items-center gap-3">
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-medium">{k.label}</span>
                    <span className="block text-xs text-snow-faint">{k.hint}</span>
                  </span>
                  <input type="checkbox" className="h-5 w-5 accent-gold" checked={k.on} onChange={(e) => save({ kinds: { [k.key]: e.target.checked } })} />
                </label>
                {k.key === "results" && resultsOn && s.results.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {s.results.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        className="chip"
                        title={`${c.name}${c.mine ? " (one of yours)" : ""}`}
                        style={c.on ? { backgroundColor: c.color, borderColor: c.color, color: "#0B0B0C" } : { borderColor: `${c.color}80`, color: c.color }}
                        onClick={() => save({ results: { [c.id]: !c.on } })}
                      >
                        {c.code}
                      </button>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
          <p className="text-xs text-snow-faint">Always sent: account and security emails, and notices from the organisers&apos; Email page.</p>
        </>
      )}
    </section>
  );
}
