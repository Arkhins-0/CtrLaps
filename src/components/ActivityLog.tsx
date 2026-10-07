"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import { Icon } from "./Icon";
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

/** Who did it (the filter): the roles, a developer, or CTR[L]APS itself. */
const ROLES = [
  { key: "", label: "Anyone" },
  { key: "developer", label: "Developer" },
  { key: "admin", label: "Admin" },
  { key: "coordinator", label: "Coordinator" },
  { key: "race_official", label: "Delegate" },
  { key: "team_manager", label: "Team manager" },
  { key: "racer", label: "Racer" },
  { key: "crew", label: "Crew" },
  { key: "security_head", label: "Security head" },
  { key: "security", label: "Security" },
  { key: "volunteer", label: "Volunteer" },
  { key: "user", label: "User" },
  { key: "system", label: "CTR[L]APS" },
] as const;

const PERIODS = [
  { key: "", label: "All time" },
  { key: "today", label: "Today" },
  { key: "7d", label: "Last 7 days" },
  { key: "30d", label: "Last 30 days" },
] as const;

type Query = { kind: string; q: string; role: string; period: string; oldest: boolean };

function url(query: Query, before: string | null): string {
  const p = new URLSearchParams();
  if (query.kind) p.set("kind", query.kind);
  if (query.q.trim()) p.set("q", query.q.trim());
  if (query.role) p.set("role", query.role);
  if (query.period) p.set("period", query.period);
  if (query.oldest) p.set("sort", "oldest");
  if (before) p.set("before", before);
  return `/api/activity?${p}`;
}

/** The log: a search, a filter (who and when), the order, a kind; more on demand. */
export function ActivityLog({ initial }: { initial: Page }) {
  const [query, setQuery] = useState<Query>({ kind: "", q: "", role: "", period: "", oldest: false });
  const [typed, setTyped] = useState("");
  const [entries, setEntries] = useState(initial.entries);
  const [next, setNext] = useState(initial.next);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const first = useRef(true);
  const asked = useRef(0);

  // The search waits for a pause in typing.
  useEffect(() => {
    const t = setTimeout(() => setQuery((q) => (q.q === typed ? q : { ...q, q: typed })), 350);
    return () => clearTimeout(t);
  }, [typed]);

  const load = async (q: Query, before: string | null) => {
    const ticket = ++asked.current;
    setBusy(true);
    setError(null);
    try {
      const page = await api<Page>(url(q, before));
      if (ticket !== asked.current) return; // A newer search has started.
      setEntries((old) => (before ? [...old, ...page.entries] : page.entries));
      setNext(page.next);
    } catch (e) {
      if (ticket === asked.current) setError(e instanceof Error ? e.message : "Could not load.");
    } finally {
      if (ticket === asked.current) setBusy(false);
    }
  };

  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    load(query, null);
  }, [query]);

  const filtered = Boolean(query.role || query.period);
  const roleLabel = ROLES.find((r) => r.key === query.role)?.label;
  const periodLabel = PERIODS.find((p) => p.key === query.period)?.label;

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <label className="relative min-w-0 flex-1">
          <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-snow-faint" />
          <input
            className="input pl-9"
            type="search"
            placeholder="Search people, actions, weekends or details"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            aria-label="Search the activity log"
          />
        </label>
        <div className="relative">
          <button
            className={`btn-icon ${filtered ? "text-gold" : "text-snow-soft hover:text-snow"}`}
            onClick={() => setFiltersOpen((o) => !o)}
            aria-expanded={filtersOpen}
            aria-label="Filter"
            title="Filter by who and when"
          >
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
              <path d="M4 6h16M7 12h10M10 18h4" />
            </svg>
          </button>
          {filtersOpen && (
            <div className="absolute right-0 z-20 mt-2 w-72 space-y-3 rounded-xl border border-night-line bg-night-panel p-3 shadow-xl">
              <label className="block">
                <span className="label">Who did it</span>
                <select className="input" value={query.role} onChange={(e) => setQuery((q) => ({ ...q, role: e.target.value }))}>
                  {ROLES.map((r) => (
                    <option key={r.key} value={r.key}>
                      {r.label}
                    </option>
                  ))}
                </select>
              </label>
              <div>
                <span className="label">When</span>
                <div className="flex flex-wrap gap-2">
                  {PERIODS.map((p) => (
                    <button
                      key={p.key}
                      className={`chip ${query.period === p.key ? "border-gold bg-gold text-ink" : "hover:border-snow/40"}`}
                      onClick={() => setQuery((q) => ({ ...q, period: p.key }))}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex justify-between">
                <button className="btn-ghost px-3 py-1 text-xs" onClick={() => setQuery((q) => ({ ...q, role: "", period: "" }))} disabled={!filtered}>
                  Clear
                </button>
                <button className="btn-gold px-3 py-1 text-xs" onClick={() => setFiltersOpen(false)}>
                  Done
                </button>
              </div>
            </div>
          )}
        </div>
        <button
          className="btn-icon text-snow-soft hover:text-snow"
          onClick={() => setQuery((q) => ({ ...q, oldest: !q.oldest }))}
          aria-label={query.oldest ? "Oldest first; show newest first" : "Newest first; show oldest first"}
          title={query.oldest ? "Oldest first" : "Newest first"}
        >
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
            {query.oldest ? <path d="M7 20V4m0 0L3 8m4-4 4 4M13 8h8M13 12h6M13 16h4" /> : <path d="M7 4v16m0 0-4-4m4 4 4-4M13 8h4M13 12h6M13 16h8" />}
          </svg>
        </button>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {KINDS.map((k) => (
          <button
            key={k.key}
            className={`chip ${query.kind === k.key ? "border-gold bg-gold text-ink" : "hover:border-snow/40"}`}
            onClick={() => setQuery((q) => (q.kind === k.key ? q : { ...q, kind: k.key }))}
          >
            {k.label}
          </button>
        ))}
        <span className="ml-auto text-xs text-snow-faint">
          {[query.role && roleLabel, query.period && periodLabel, query.oldest ? "Oldest first" : "Newest first"].filter(Boolean).join(" · ")}
        </span>
      </div>
      {error && <p className="error">{error}</p>}
      <ul className={`card divide-y divide-night-line p-0 ${busy ? "opacity-70" : ""}`}>
        {entries.length === 0 && <li className="p-4 text-sm text-snow-faint">{query.q || filtered ? "Nothing matches." : "Nothing logged yet."}</li>}
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
        <button className="btn-ghost w-full" onClick={() => load(query, next)} disabled={busy}>
          {busy ? "Loading…" : query.oldest ? "Show newer" : "Show older"}
        </button>
      )}
    </div>
  );
}
