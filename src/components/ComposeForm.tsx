"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { api } from "@/lib/client";
import { ROLE_LABEL, type Role } from "@/lib/roles";
import type { RosterCategory } from "@/lib/categoryChannels";
import type { PublicUser } from "@/lib/users";
import { Avatar } from "./Avatar";
import { MessageComposer } from "./MessageComposer";
import { contrastText } from "@/lib/colors";

/** Pick who, below you, gets the message — by person, a whole role, or a race category's people at once. */
export function ComposeForm({ people, categories = [] }: { people: PublicUser[]; categories?: RosterCategory[] }) {
  const router = useRouter();
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState("");
  const [sent, setSent] = useState<number | null>(null);

  const roles = useMemo(() => Array.from(new Set(people.map((p) => p.role))) as Role[], [people]);
  const shown = people.filter((p) => !filter || `${p.name ?? ""} ${p.email} ${p.teamName ?? ""}`.toLowerCase().includes(filter.toLowerCase()));

  const toggle = (id: string) =>
    setPicked((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  const toggleRole = (role: Role) => toggleIds(people.filter((p) => p.role === role).map((p) => p.id));
  const toggleIds = (ids: string[]) => {
    const all = ids.every((id) => picked.has(id));
    setPicked((s) => {
      const n = new Set(s);
      ids.forEach((id) => (all ? n.delete(id) : n.add(id)));
      return n;
    });
  };

  if (sent !== null)
    return (
      <div className="card space-y-3 text-sm">
        <p>Sent to {sent} {sent === 1 ? "person" : "people"}.</p>
        <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => router.push("/home")}>
          Back to inbox
        </button>
      </div>
    );

  return (
    <div className="space-y-4">
      <div className="card space-y-3">
        <div className="flex flex-wrap gap-2">
          {roles.map((r) => {
            const ids = people.filter((p) => p.role === r);
            const all = ids.every((p) => picked.has(p.id));
            return (
              <button key={r} type="button" className={`chip ${all ? "border-gold bg-gold text-ink" : "hover:border-snow/40"}`} onClick={() => toggleRole(r)}>
                All {ROLE_LABEL[r].toLowerCase()}s · {ids.length}
              </button>
            );
          })}
        </div>
        {/* A category's people who are below you: its racers, its teams' crew and managers, its race officials. */}
        {categories.some((c) => c.memberIds.some((id) => people.some((p) => p.id === id))) && (
          <div className="flex flex-wrap gap-2">
            {categories.map((c) => {
              const ids = c.memberIds.filter((id) => people.some((p) => p.id === id));
              if (ids.length === 0) return null;
              const all = ids.every((id) => picked.has(id));
              return (
                <button
                  key={c.id}
                  type="button"
                  className="chip"
                  style={all ? { backgroundColor: c.color, borderColor: c.color, color: contrastText(c.color) } : { borderColor: `${c.color}80`, color: c.color }}
                  onClick={() => toggleIds(ids)}
                  title={c.name}
                >
                  All {c.code} · {ids.length}
                </button>
              );
            })}
          </div>
        )}
        <input className="input" placeholder="Search people" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <div className="max-h-72 divide-y divide-night-line overflow-y-auto">
          {shown.map((p) => (
            <label key={p.id} className="row cursor-pointer">
              <input type="checkbox" className="accent-gold" checked={picked.has(p.id)} onChange={() => toggle(p.id)} />
              <Avatar src={p.photoUrl} name={p.name ?? p.email} size={30} preview={false} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm">{p.name ?? p.email}</span>
                <span className="block truncate text-xs text-snow-faint">
                  {p.roleLabel}
                  {p.teamName ? ` · ${p.teamName}` : ""}
                </span>
              </span>
            </label>
          ))}
        </div>
        <p className="text-xs text-snow-faint">{picked.size} selected</p>
      </div>
      <MessageComposer
        send={async (draft) => {
          if (picked.size === 0) throw new Error("Pick at least one person.");
          const r = await api<{ delivered: number }>("/api/messages", { method: "POST", json: { ...draft, recipientIds: Array.from(picked) } });
          setSent(r.delivered);
        }}
      />
    </div>
  );
}
