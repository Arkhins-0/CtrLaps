"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { DEVELOPER_LABEL, ROLE_LABEL, ROLES, type Role } from "@/lib/roles";
import type { RosterCategory } from "@/lib/categoryChannels";
import type { PublicUser } from "@/lib/users";
import { GroupTitle, SQUARE_BUTTON, SearchPill } from "./AppUI";
import { Avatar } from "./Avatar";
import { Icon } from "./Icon";
import { StatusBadge } from "./StatusBadge";

const KEY = "ctrlaps:people-hidden";
/** A list group: a role, or developers (admins who answer support), who show apart. */
type Group = Role | "developer";
const GROUPS: Group[] = ["developer", ...ROLES];
const GROUP_LABEL = (g: Group): string => (g === "developer" ? DEVELOPER_LABEL : ROLE_LABEL[g]);
/** A group's title: Admins, Delegates; Security and Crew stay as they are. */
const GROUP_PLURAL = (g: Group): string => (g === "security" || g === "crew" ? GROUP_LABEL(g) : `${GROUP_LABEL(g)}s`);
const groupOf = (p: PublicUser): Group => (p.isDev ? "developer" : p.role);
/** People who registered and have no role yet, and developers, start hidden. */
const DEFAULT_HIDDEN: Group[] = ["user", "developer"];

/** Everyone below the viewer by role, with a filter: which roles show, and one race category or one team. */
export function PeopleList({ people, emptyText, categories = [] }: { people: PublicUser[]; emptyText: string; categories?: RosterCategory[] }) {
  const [hidden, setHidden] = useState<Group[]>(DEFAULT_HIDDEN);
  const [category, setCategory] = useState("");
  const [team, setTeam] = useState("");
  const [query, setQuery] = useState("");
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

  const toggle = (r: Group) => {
    const next = hidden.includes(r) ? hidden.filter((x) => x !== r) : [...hidden, r];
    setHidden(next);
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch {}
  };

  const teams = Array.from(new Set(people.map((p) => p.teamName?.trim()).filter((t): t is string => Boolean(t)))).sort((a, b) => a.localeCompare(b));
  const inCategory = categories.find((c) => c.id === category)?.memberIds;
  const kept = people.filter(
    (p) => (!inCategory || inCategory.includes(p.id)) && (!team || p.teamName?.trim().toLowerCase() === team.toLowerCase()),
  );
  const present = GROUPS.filter((r) => people.some((p) => groupOf(p) === r));
  const groups = present
    .filter((r) => !hidden.includes(r))
    .map((r) => ({ role: r, people: kept.filter((p) => groupOf(p) === r) }))
    .filter((g) => g.people.length > 0);
  const narrowed = Boolean(category || team);

  // Each person's race category codes, for the line under their name.
  const codes = new Map<string, string[]>();
  for (const c of categories) for (const id of c.memberIds) codes.set(id, [...(codes.get(id) ?? []), c.code]);
  const q = query.trim().toLowerCase();
  const shown = q ? groups.map((g) => ({ ...g, people: g.people.filter((p) => `${p.name ?? ""} ${p.email} ${p.roleLabel} ${p.teamName ?? ""}`.toLowerCase().includes(q)) })).filter((g) => g.people.length > 0) : groups;
  const cat = categories.find((c) => c.id === category);

  return (
    <div>
      <div className="flex items-center gap-2.5">
        <SearchPill query={query} onChange={setQuery} placeholder="Name, designation or team" />
        {present.length > 0 && (
          <div className="relative" ref={menu}>
            <button type="button" className={SQUARE_BUTTON} onClick={() => setOpen((v) => !v)} aria-expanded={open} aria-label="Filter">
              <Icon name="filter" className="h-6 w-6" />
              {narrowed && <span className="absolute right-2.5 top-2.5 h-2 w-2 rounded-full bg-gold" aria-label="Filtered" />}
            </button>
            {open && (
              <div className="absolute right-0 top-full z-20 mt-2 w-56 rounded-xl border border-night-line bg-night-panel p-2 shadow-xl">
                <p className="px-2 pb-1 pt-1 text-[11px] font-semibold uppercase tracking-wide text-snow-faint">Show</p>
                {present.map((r) => (
                  <label key={r} className="flex cursor-pointer items-center gap-3 rounded-lg px-2 py-2 text-sm hover:bg-snow/5">
                    <input type="checkbox" className="h-4 w-4 accent-gold" checked={!hidden.includes(r)} onChange={() => toggle(r)} />
                    {GROUP_LABEL(r)}
                  </label>
                ))}
                {categories.length > 0 && (
                  <>
                    <p className="px-2 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-snow-faint">Category</p>
                    <select className="input py-1.5 text-sm" value={category} onChange={(e) => setCategory(e.target.value)}>
                      <option value="">Any category</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.code} — {c.name}
                        </option>
                      ))}
                    </select>
                  </>
                )}
                {teams.length > 0 && (
                  <>
                    <p className="px-2 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-snow-faint">Team</p>
                    <select className="input py-1.5 text-sm" value={team} onChange={(e) => setTeam(e.target.value)}>
                      <option value="">Any team</option>
                      {teams.map((t) => (
                        <option key={t} value={t}>
                          {t}
                        </option>
                      ))}
                    </select>
                  </>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* What the filter narrows to, each with a tap to take it off. */}
      {narrowed && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {cat && (
            <button type="button" className="chip border-gold bg-gold text-ink" onClick={() => setCategory("")}>
              {cat.code} ✕
            </button>
          )}
          {team && (
            <button type="button" className="chip border-gold bg-gold text-ink" onClick={() => setTeam("")}>
              {team} ✕
            </button>
          )}
        </div>
      )}

      {people.length === 0 && <p className="mt-4 text-sm text-snow-faint">{emptyText}</p>}
      {people.length > 0 && shown.length === 0 && (
        <p className="mt-4 text-sm text-snow-faint">
          {q ? "No one matches." : narrowed ? "Nobody matches this category or team." : "Everyone here is hidden by the filter. Use Filter to show them."}
        </p>
      )}

      {shown.map((g) => (
        <section key={g.role}>
          <GroupTitle title={GROUP_PLURAL(g.role)} count={g.people.length} />
          <div className="grid grid-cols-1 gap-x-4 lg:grid-cols-2">
            {g.people.map((p) => {
              const invited = !p.name;
              const line = invited ? "Invite not accepted" : [p.teamName?.trim(), (codes.get(p.id) ?? []).join(" · ")].filter(Boolean).join(" · ") || p.email;
              return (
                <Link key={p.id} href={`/people/${p.id}`} className="flex items-center gap-3.5 rounded-xl px-1 py-2.5 transition-colors hover:bg-snow/5">
                  <Avatar src={p.photoUrl} name={p.name ?? p.email} size={46} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-bold">{p.name ?? p.email}</span>
                    <span className={`block truncate text-sm ${invited ? "text-gold" : "text-snow-soft"}`}>{line}</span>
                  </span>
                  {p.status !== "active" && p.status !== "pending" && <StatusBadge status={p.status} />}
                </Link>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}
