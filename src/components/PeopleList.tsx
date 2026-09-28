"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ROLE_LABEL, ROLES, type Role } from "@/lib/roles";
import type { PublicUser } from "@/lib/users";
import { Avatar } from "./Avatar";
import { Icon } from "./Icon";
import { StatusBadge } from "./StatusBadge";

const KEY = "ctrlaps:people-hidden-roles";
/** People who registered and have no role yet start hidden. */
const DEFAULT_HIDDEN: Role[] = ["user"];

/** Everyone below the viewer by role, with a filter to show or hide each role. */
export function PeopleList({ people, emptyText }: { people: PublicUser[]; emptyText: string }) {
  const [hidden, setHidden] = useState<Role[]>(DEFAULT_HIDDEN);
  const [open, setOpen] = useState(false);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(KEY);
      if (saved) setHidden(JSON.parse(saved));
    } catch {}
  }, []);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (!menu.current?.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);

  const toggle = (r: Role) => {
    const next = hidden.includes(r) ? hidden.filter((x) => x !== r) : [...hidden, r];
    setHidden(next);
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch {}
  };

  const present = ROLES.filter((r) => people.some((p) => p.role === r));
  const groups = present.filter((r) => !hidden.includes(r)).map((r) => ({ role: r, people: people.filter((p) => p.role === r) }));

  return (
    <div className="space-y-5">
      {present.length > 0 && (
        <div className="relative flex justify-end" ref={menu}>
          <button type="button" className="btn-ghost flex items-center gap-2 px-3 py-1.5 text-xs" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
            <Icon name="filter" className="h-4 w-4 text-gold" />
            Filter
          </button>
          {open && (
            <div className="absolute right-0 top-full z-20 mt-2 w-56 rounded-xl border border-night-line bg-night-panel p-2 shadow-xl">
              <p className="px-2 pb-1 pt-1 text-[11px] font-semibold uppercase tracking-wide text-snow-faint">Show</p>
              {present.map((r) => (
                <label key={r} className="flex cursor-pointer items-center gap-3 rounded-lg px-2 py-2 text-sm hover:bg-snow/5">
                  <input type="checkbox" className="h-4 w-4 accent-gold" checked={!hidden.includes(r)} onChange={() => toggle(r)} />
                  {ROLE_LABEL[r]}
                </label>
              ))}
            </div>
          )}
        </div>
      )}

      {people.length === 0 && <p className="card text-sm text-snow-faint">{emptyText}</p>}
      {people.length > 0 && groups.length === 0 && (
        <p className="card text-sm text-snow-faint">Everyone here is hidden by the filter. Use Filter to show them.</p>
      )}

      {groups.map((g) => (
        <section key={g.role}>
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-snow-faint">
            {ROLE_LABEL[g.role]}s · {g.people.length}
          </h2>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-3">
            {g.people.map((p) => (
              <Link key={p.id} href={`/people/${p.id}`} className="row border border-night-line bg-night-panel/60 hover:border-gold/40">
                <Avatar src={p.photoUrl} name={p.name ?? p.email} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{p.name ?? p.email}</span>
                  <span className="block truncate text-xs text-snow-faint">
                    {p.name ? p.email : "Invite not accepted"}
                    {p.teamName ? ` · ${p.teamName}` : ""}
                  </span>
                </span>
                <StatusBadge status={p.status} />
              </Link>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
