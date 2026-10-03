"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { Dashboard, DashboardPerson } from "@/lib/dashboard";

const KEY = "ctrlaps.dashboard.open";

/**
 * The admin's and coordinator's card on Home: today's sessions and time changes, and people to nudge. Folds to one
 * line ("Today: 4 sessions · 9 to check"); the choice is remembered on this browser. Hidden when there is nothing.
 */
export function DashboardCard({ data }: { data: Dashboard }) {
  const [open, setOpen] = useState(true);
  const [shown, setShown] = useState<string | null>(null);
  useEffect(() => {
    try {
      const v = localStorage.getItem(KEY);
      if (v !== null) setOpen(v === "1");
    } catch {
      // Private browsing: start open.
    }
  }, []);
  const toggle = () => {
    setOpen((o) => {
      try {
        localStorage.setItem(KEY, o ? "0" : "1");
      } catch {
        // Not remembered; fine.
      }
      return !o;
    });
  };

  const toCheck = data.pending.length + data.unfinished.length + data.suspended.length;
  if (data.sessions.length === 0 && data.changes.length === 0 && toCheck === 0) return null;
  const summary = [
    data.sessions.length ? `${data.sessions.length} session${data.sessions.length === 1 ? "" : "s"}` : null,
    data.changes.length ? `${data.changes.length} time change${data.changes.length === 1 ? "" : "s"}` : null,
    toCheck ? `${toCheck} to check` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  const people = (key: string, label: string, list: DashboardPerson[]) =>
    list.length > 0 && (
      <div>
        <button className="flex w-full items-center justify-between py-1.5 text-left text-sm hover:text-snow" onClick={() => setShown(shown === key ? null : key)} aria-expanded={shown === key}>
          <span className="text-snow-soft">
            <span className="font-semibold text-snow">{list.length}</span> {label}
          </span>
          <span className="text-gold">{shown === key ? "▾" : "›"}</span>
        </button>
        {shown === key && (
          <div className="flex flex-wrap gap-1.5 pb-2">
            {list.map((p) => (
              <Link key={p.id} href={`/people/${p.id}`} className="chip text-xs hover:border-gold/60">
                {p.name}
              </Link>
            ))}
          </div>
        )}
      </div>
    );

  return (
    <section className="card border-gold/30">
      <button className="flex w-full items-center justify-between gap-2 text-left" onClick={toggle} aria-expanded={open}>
        <span className="min-w-0">
          <span className="section-title text-gold">Today</span>
          {!open && <span className="ml-2 text-sm text-snow-soft">{summary}</span>}
        </span>
        <span className="text-xs text-snow-faint">{open ? "Hide" : "Show"}</span>
      </button>
      {open && (
        <div className="mt-2 divide-y divide-night-line">
          {data.sessions.length > 0 && (
            <div className="space-y-0.5 py-1.5 text-sm">
              {data.sessions.map((s) => (
                <Link key={s.id} href={`/w/${s.weekendId}`} className="flex justify-between gap-2 hover:text-snow">
                  <span className="truncate text-snow-soft">
                    {s.name} <span className="text-snow-faint">· {s.weekendName}</span>
                  </span>
                  <span className="shrink-0 text-snow-faint">{s.track}</span>
                </Link>
              ))}
            </div>
          )}
          {data.changes.length > 0 && (
            <div className="space-y-0.5 py-1.5 text-sm text-gold">
              {data.changes.map((c, i) => (
                <p key={i}>{c}</p>
              ))}
            </div>
          )}
          {people("pending", "haven't accepted their invite", data.pending)}
          {people("unfinished", "haven't finished their profile", data.unfinished)}
          {people("suspended", "suspended", data.suspended)}
        </div>
      )}
    </section>
  );
}
