"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { VolunteerGroup } from "@/lib/volunteers";
import { WhenLabel } from "../ChatList";
import { Dialog } from "../groups/Dialog";

/** The Volunteers page: each volunteer group's chat this person sees, and (admins, coordinators) a new group. */
export function VolunteerGroups({
  groups,
  canCreate,
  isAdmin,
  coordinators,
}: {
  groups: VolunteerGroup[];
  canCreate: boolean;
  isAdmin: boolean;
  coordinators: { id: string; name: string }[];
}) {
  const router = useRouter();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [lead, setLead] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ id: string }>("/api/volunteer-groups", { method: "POST", json: { name, coordinatorId: isAdmin ? lead : undefined } });
      router.push(`/chats/volunteers/${r.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not make the group.");
      setBusy(false);
    }
  };

  return (
    <div className="-mx-4 min-h-0 flex-1 overflow-y-auto pb-4 sm:-mx-6 lg:mx-0">
      <div className="flex items-center gap-2 px-4 py-2 sm:px-6 lg:px-3">
        <p className="section-title flex-1">Volunteer groups</p>
        {canCreate && (
          <button className="btn-gold px-3.5 py-1.5 text-xs" onClick={() => setCreating(true)}>
            New group
          </button>
        )}
      </div>
      {groups.length === 0 && (
        <p className="px-4 py-3 text-sm text-snow-faint sm:px-6 lg:px-3">{canCreate ? "No volunteer groups yet. Make the first one." : "You're not in a volunteer group yet."}</p>
      )}
      {groups.map((g, i) => (
        <div key={g.id}>
          {i > 0 && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
          <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3">
            <Link href={`/chats/${g.conversationId}`} className="flex min-w-0 flex-1 items-center gap-3">
              <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] text-xl ${g.open ? "bg-gold/15" : "border border-night-line bg-night-panel"}`} aria-hidden>
                🦺
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[15px] font-medium">{g.name}</span>
                <span className={`block truncate text-[13px] ${g.unread > 0 ? "text-snow" : "text-snow-faint"}`}>
                  {g.lastMessage ?? `${g.volunteers} volunteer${g.volunteers === 1 ? "" : "s"}${g.open ? "" : " · closed"}`}
                </span>
                <span className="block truncate text-[11px] text-snow-faint">
                  {g.coordinator ? `Led by ${g.coordinator.name}` : "No coordinator"} · {g.volunteers} volunteer{g.volunteers === 1 ? "" : "s"}
                  {g.open ? "" : " · chat closed"}
                </span>
              </span>
            </Link>
            <span className="flex shrink-0 flex-col items-end gap-1">
              {g.lastMessageAt && (
                <span className={`text-[11px] ${g.unread > 0 ? "text-gold" : "text-snow-faint"}`}>
                  <WhenLabel iso={g.lastMessageAt} />
                </span>
              )}
              {g.unread > 0 && <span className="badge">{g.unread > 99 ? "99+" : g.unread}</span>}
              {g.canManage && (
                <Link href={`/chats/volunteers/${g.id}`} className="rounded px-1 text-[11px] font-semibold text-gold hover:underline">
                  Manage
                </Link>
              )}
            </span>
          </div>
        </div>
      ))}
      {creating && (
        <Dialog title="New volunteer group" onClose={() => setCreating(false)}>
          <form onSubmit={create} className="space-y-3">
            {error && <p className="error">{error}</p>}
            <label className="block">
              <span className="label">Name</span>
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Marshals" maxLength={80} required autoFocus />
            </label>
            {isAdmin && (
              <label className="block">
                <span className="label">Led by</span>
                <select className="input" value={lead} onChange={(e) => setLead(e.target.value)} required>
                  <option value="">Pick a coordinator</option>
                  {coordinators.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div className="flex justify-end gap-2">
              <button type="button" className="btn-ghost" onClick={() => setCreating(false)} disabled={busy}>
                Cancel
              </button>
              <button className="btn-gold" disabled={busy || name.trim().length < 2 || (isAdmin && !lead)}>
                {busy ? "Making…" : "Make group"}
              </button>
            </div>
          </form>
        </Dialog>
      )}
    </div>
  );
}
