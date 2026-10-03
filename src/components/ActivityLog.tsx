"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/client";
import { LocalTime } from "./LocalTime";

type Entry = {
  id: string;
  at: string;
  action: string;
  title: string;
  detail: string | null;
  actor: { id: string; name: string; roleLabel: string } | null;
  target: { id: string; name: string; kind: "person" | "weekend" } | null;
};
type Page = { entries: Entry[]; next: string | null };

const KINDS = [
  { key: "", label: "All" },
  { key: "messages", label: "Messages" },
  { key: "schedule", label: "Schedule" },
  { key: "results", label: "Results" },
  { key: "people", label: "People" },
] as const;

/** The log, newest first: a filter by kind, and more on demand. */
export function ActivityLog({ initial }: { initial: Page }) {
  const [kind, setKind] = useState("");
  const [entries, setEntries] = useState(initial.entries);
  const [next, setNext] = useState(initial.next);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async (k: string, before: string | null) => {
    setBusy(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (k) params.set("kind", k);
      if (before) params.set("before", before);
      const page = await api<Page>(`/api/activity?${params}`);
      setEntries((old) => (before ? [...old, ...page.entries] : page.entries));
      setNext(page.next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {KINDS.map((k) => (
          <button
            key={k.key}
            className={`chip ${kind === k.key ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`}
            onClick={() => {
              if (kind === k.key) return;
              setKind(k.key);
              load(k.key, null);
            }}
            disabled={busy}
          >
            {k.label}
          </button>
        ))}
      </div>
      {error && <p className="error">{error}</p>}
      <ul className="card divide-y divide-night-line p-0">
        {entries.length === 0 && <li className="p-4 text-sm text-snow-faint">Nothing logged yet.</li>}
        {entries.map((e) => (
          <li key={e.id} className="space-y-0.5 px-4 py-3 text-sm">
            <div className="flex flex-wrap items-baseline justify-between gap-x-3">
              <p>
                <span className="font-medium">{e.actor ? e.actor.name : "CTR[L]APS"}</span>
                {e.actor?.roleLabel && <span className="text-snow-faint"> · {e.actor.roleLabel}</span>}
              </p>
              <span className="text-xs text-snow-faint">
                <LocalTime iso={e.at} />
              </span>
            </div>
            <p className="text-snow-soft">
              {e.title}
              {e.target && (
                <>
                  {" — "}
                  <Link href={e.target.kind === "person" ? `/people/${e.target.id}` : `/w/${e.target.id}`} className="text-gold hover:underline">
                    {e.target.name}
                  </Link>
                </>
              )}
            </p>
            {e.detail && <p className="break-words text-xs text-snow-faint">{e.detail}</p>}
          </li>
        ))}
      </ul>
      {next && (
        <button className="btn-ghost w-full" onClick={() => load(kind, next)} disabled={busy}>
          {busy ? "Loading…" : "Show older"}
        </button>
      )}
    </div>
  );
}
