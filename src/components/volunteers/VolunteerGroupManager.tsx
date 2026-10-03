"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/client";
import type { GroupDetail, Permission } from "@/lib/volunteers";
import { Avatar } from "../Avatar";
import { Icon } from "../Icon";

const PERMISSIONS: { key: Permission; label: string }[] = [
  { key: "full", label: "Can chat" },
  { key: "no_messages", label: "Can't send" },
  { key: "read_only", label: "Read only" },
];

/** Manage one volunteer group (its coordinator or an admin): name, coordinator, open or closed, its volunteers. */
export function VolunteerGroupManager({ initial }: { initial: GroupDetail }) {
  const [g, setG] = useState(initial);
  const [name, setName] = useState(initial.name);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [adding, setAdding] = useState("");
  // Move several at once: ticked volunteers, a search, and where they go.
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [moveTo, setMoveTo] = useState("");

  const run = async (fn: () => Promise<{ group: GroupDetail | null }>, done?: string) => {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      const r = await fn();
      if (r.group) setG(r.group);
      else window.location.href = "/chats/volunteers"; // Handed to someone else: no longer ours to manage.
      if (done) setNote(done);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  };
  const patch = (json: Record<string, unknown>, done: string) => run(() => api(`/api/volunteer-groups/${g.id}`, { method: "PATCH", json }), done);
  const permission = (userId: string, p: Permission) =>
    run(async () => {
      await api(`/api/groups/${g.conversationId}/members/${userId}`, { method: "PATCH", json: { permission: p } });
      return api(`/api/volunteer-groups/${g.id}`);
    });
  const movePicked = (to: string) => {
    const ids = Array.from(picked);
    const target = to === "out" ? null : to;
    const name = target ? g.otherGroups.find((o) => o.id === target)?.name ?? "another group" : "no group";
    if (!confirm(`Move ${ids.length} volunteer${ids.length === 1 ? "" : "s"} to ${name}?`)) return;
    run(() => api(`/api/volunteer-groups/${g.id}/volunteers/move`, { method: "POST", json: { userIds: ids, toGroupId: target } }), `Moved ${ids.length} to ${name}.`).then(() => setPicked(new Set()));
  };
  const bringIn = (userId: string) => run(() => api(`/api/volunteer-groups/${g.id}/volunteers`, { method: "POST", json: { userId } }), "Added.");

  const shown = g.volunteers.filter((v) => !search.trim() || v.name.toLowerCase().includes(search.trim().toLowerCase()));
  const allPicked = shown.length > 0 && shown.every((v) => picked.has(v.id));

  return (
    <div className="h-full min-h-0 space-y-4 overflow-y-auto pb-4">
      <div className="flex items-center gap-2">
        <Link href="/chats/volunteers" className="btn-icon" aria-label="Back to volunteer groups">
          <Icon name="back" className="h-5 w-5" />
        </Link>
        <h1 className="flex-1 truncate text-lg font-semibold">{g.name}</h1>
        <Link href={`/chats/${g.conversationId}`} className="btn-gold px-3.5 py-1.5 text-xs">
          Open chat
        </Link>
      </div>
      {error && <p className="error">{error}</p>}
      {note && <p className="rounded-xl border border-gold/40 bg-gold/10 px-3.5 py-2.5 text-sm text-gold">{note}</p>}

      <section className="card space-y-3">
        <form
          className="flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (name.trim() !== g.name) patch({ name }, "Renamed.");
          }}
        >
          <label className="block flex-1">
            <span className="label">Name</span>
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
          </label>
          <button className="btn-ghost px-3.5 py-2 text-xs" disabled={busy || name.trim().length < 2 || name.trim() === g.name}>
            Save
          </button>
        </form>
        <label className="block">
          <span className="label">Led by</span>
          <select
            className="input"
            value={g.coordinator?.id ?? ""}
            onChange={(e) => e.target.value && confirm("Hand the group to this coordinator? Its volunteers and chat go with it.") && patch({ coordinatorId: e.target.value }, "Handed over.")}
            disabled={busy}
          >
            {!g.coordinator && <option value="">Pick a coordinator</option>}
            {g.coordinators.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm text-snow-soft">{g.open ? "The chat is open." : "The chat is closed: everyone can read it, nobody can send."}</p>
          <button className={g.open ? "btn-ghost px-3.5 py-1.5 text-xs" : "btn-gold px-3.5 py-1.5 text-xs"} onClick={() => patch({ open: !g.open }, g.open ? "Chat closed." : "Chat opened.")} disabled={busy}>
            {g.open ? "Close chat" : "Open chat"}
          </button>
        </div>
      </section>

      <section className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <p className="section-title flex-1">Volunteers · {g.volunteers.length}</p>
          {g.volunteers.length > 3 && <input className="input w-48 py-1 text-xs" placeholder="Search volunteers" value={search} onChange={(e) => setSearch(e.target.value)} />}
          {shown.length > 0 && (
            <button className="btn-ghost px-3 py-1 text-xs" onClick={() => setPicked(allPicked ? new Set() : new Set(shown.map((v) => v.id)))}>
              {allPicked ? "None" : "Select all"}
            </button>
          )}
        </div>
        {picked.size > 0 && (
          <div className="card flex flex-wrap items-center gap-2 border-gold/40 py-2">
            <p className="flex-1 text-sm">
              <span className="font-semibold">{picked.size}</span> selected
            </p>
            <select className="input w-auto py-1 text-xs" value={moveTo} onChange={(e) => setMoveTo(e.target.value)} disabled={busy} aria-label="Move to">
              <option value="">Move to…</option>
              {g.otherGroups.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                  {o.coordinator ? ` (${o.coordinator})` : ""}
                </option>
              ))}
              <option value="out">No group</option>
            </select>
            <button className="btn-gold px-3.5 py-1.5 text-xs" onClick={() => movePicked(moveTo)} disabled={busy || !moveTo}>
              Move
            </button>
          </div>
        )}
        <div className="card divide-y divide-night-line p-1.5">
          {g.volunteers.length === 0 && <p className="p-3 text-sm text-snow-faint">No volunteers in this group yet.</p>}
          {shown.map((v) => (
            <div key={v.id} className="flex flex-wrap items-center gap-3 px-2.5 py-2.5">
              <input
                type="checkbox"
                className="h-4 w-4 accent-gold"
                checked={picked.has(v.id)}
                onChange={(e) => setPicked((p) => { const n = new Set(p); if (e.target.checked) n.add(v.id); else n.delete(v.id); return n; })}
                aria-label={`Select ${v.name}`}
              />
              <Avatar src={v.photoUrl} name={v.name} size={36} />
              <Link href={`/people/${v.id}`} className="min-w-0 flex-1 truncate text-sm font-medium hover:underline">
                {v.name}
                {v.status !== "active" && <span className="ml-1.5 text-xs text-snow-faint">· {v.status}</span>}
              </Link>
              <div className="flex flex-wrap items-center gap-1.5">
                {PERMISSIONS.map((p) => (
                  <button
                    key={p.key}
                    className={`chip px-2 py-0.5 text-[11px] ${v.permission === p.key ? (p.key === "full" ? "border-gold bg-gold text-night" : "border-danger bg-danger/15 text-danger") : "hover:border-snow/40"}`}
                    onClick={() => v.permission !== p.key && permission(v.id, p.key)}
                    disabled={busy}
                    aria-pressed={v.permission === p.key}
                  >
                    {p.label}
                  </button>
                ))}

              </div>
            </div>
          ))}
        </div>
      </section>

      {g.unassigned.length > 0 && (
        <section className="card space-y-2">
          <p className="section-title">Volunteers in no group · {g.unassigned.length}</p>
          <div className="flex gap-2">
            <select className="input flex-1" value={adding} onChange={(e) => setAdding(e.target.value)} disabled={busy}>
              <option value="">Pick a volunteer</option>
              {g.unassigned.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name}
                </option>
              ))}
            </select>
            <button
              className="btn-gold px-3.5 py-1.5 text-xs"
              onClick={() => {
                if (adding) bringIn(adding);
                setAdding("");
              }}
              disabled={busy || !adding}
            >
              Add
            </button>
          </div>
        </section>
      )}
    </div>
  );
}
