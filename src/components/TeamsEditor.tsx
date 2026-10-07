"use client";

import { useState } from "react";
import { api } from "@/lib/client";
import type { Category } from "@/lib/categories";
import { usePendingEdits } from "@/lib/pendingEdits";
import type { Team } from "@/lib/teams";
import { Icon } from "./Icon";
import { SaveBar } from "./SaveBar";
import { contrastText } from "@/lib/colors";

/**
 * Teams and the race categories each is entered in this season. Tapping categories and renaming wait, so many teams
 * can be set up and saved together from the bar at the bottom; adding or deleting a team happens at once.
 */
export function TeamsEditor({ initial, categories }: { initial: Team[]; categories: Category[] }) {
  const [teams, setTeams] = useState(initial);
  const pending = usePendingEdits<Team>();
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const [adding, setAdding] = useState<{ name: string; categoryIds: string[] } | null>(null);
  const [search, setSearch] = useState("");

  const act = async (key: string, fn: () => Promise<{ teams: Team[] }>) => {
    setBusy(key);
    setError(null);
    try {
      setTeams((await fn()).teams);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
      return false;
    } finally {
      setBusy(null);
    }
  };

  const toggle = (t: Team, id: string) => {
    const now = pending.view(t).categoryIds;
    pending.change(t, { categoryIds: now.includes(id) ? now.filter((x) => x !== id) : [...now, id] });
  };

  const saveAll = async () => {
    setError(null);
    setNotice(null);
    let latest: Team[] | null = null;
    const r = await pending.saveAll(async (id, patch) => {
      latest = (await api<{ teams: Team[] }>(`/api/teams/${id}`, { method: "PATCH", json: patch })).teams;
    });
    if (latest) setTeams(latest);
    if (r.failed > 0) setError(`${r.saved > 0 ? `Saved ${r.saved}. ` : ""}${r.failed} could not be saved (${r.error}). They're still here to try again.`);
    else setNotice(r.saved === 1 ? "Saved." : `Saved ${r.saved} teams.`);
  };

  const Chips = ({ selected, onToggle, disabled }: { selected: string[]; onToggle: (id: string) => void; disabled?: boolean }) => (
    <div className="flex flex-wrap gap-1.5">
      {categories.map((c) => {
        const on = selected.includes(c.id);
        return (
          <button
            key={c.id}
            type="button"
            className="chip px-2 py-0.5 text-[11px]"
            style={on ? { backgroundColor: c.color, borderColor: c.color, color: contrastText(c.color) } : { borderColor: `${c.color}66`, color: c.color }}
            title={c.name}
            onClick={() => onToggle(c.id)}
            disabled={disabled}
            aria-pressed={on}
          >
            {c.code}
          </button>
        );
      })}
    </div>
  );

  const shown = teams.filter((t) => !search.trim() || t.name.toLowerCase().includes(search.trim().toLowerCase()));

  return (
    <div className="space-y-4">
      {error && <p className="error">{error}</p>}
      {notice && !error && <p className="text-sm text-snow-soft" role="status">{notice}</p>}
      <div className="flex flex-wrap gap-2">
        <input className="input min-w-0 flex-1" placeholder="Search teams" value={search} onChange={(e) => setSearch(e.target.value)} />
        <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => setAdding({ name: "", categoryIds: [] })}>
          Add team
        </button>
      </div>
      {adding && (
        <form
          className="card space-y-3"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await act("new", () => api("/api/teams", { method: "POST", json: adding }))) setAdding(null);
          }}
        >
          <input className="input" required minLength={2} placeholder="Team name" value={adding.name} onChange={(e) => setAdding({ ...adding, name: e.target.value })} />
          <div>
            <span className="label">Races in</span>
            <Chips
              selected={adding.categoryIds}
              onToggle={(id) =>
                setAdding({ ...adding, categoryIds: adding.categoryIds.includes(id) ? adding.categoryIds.filter((x) => x !== id) : [...adding.categoryIds, id] })
              }
            />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setAdding(null)} disabled={busy === "new"}>
              Cancel
            </button>
            <button className="btn-gold px-4 py-1.5 text-xs" disabled={busy === "new"}>
              {busy === "new" ? "Saving…" : "Add team"}
            </button>
          </div>
        </form>
      )}
      {teams.length === 0 && <p className="card text-sm text-snow-faint">No teams yet.</p>}
      <ul className="grid grid-cols-1 gap-2 lg:grid-cols-2">
        {shown.map((saved) => {
          const t = pending.view(saved);
          return (
          <li key={t.id} className={`card space-y-2 p-4 ${pending.isChanged(t.id) ? "border-gold/60" : ""}`}>
            <div className="flex items-center gap-2">
              {renaming?.id === t.id ? (
                <form
                  className="flex flex-1 gap-2"
                  onSubmit={async (e) => {
                    e.preventDefault();
                    if (renaming.name.trim().length < 2) return;
                    pending.change(saved, { name: renaming.name.trim() });
                    setRenaming(null);
                  }}
                >
                  <input className="input flex-1 py-1.5 text-sm" autoFocus value={renaming.name} onChange={(e) => setRenaming({ id: t.id, name: e.target.value })} />
                  <button className="btn-gold px-3 py-1 text-xs">Done</button>
                  <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={() => setRenaming(null)}>Cancel</button>
                </form>
              ) : (
                <>
                  <p className="min-w-0 flex-1 truncate font-semibold">{t.name}</p>
                  <span className="text-xs text-snow-faint">{t.members === 1 ? "1 person" : `${t.members} people`}</span>
                  <button className="btn-icon" aria-label={`Rename ${t.name}`} onClick={() => setRenaming({ id: t.id, name: t.name })}>
                    <Icon name="edit" className="h-4 w-4" />
                  </button>
                  <button
                    className="btn-icon text-danger/80 hover:text-danger"
                    aria-label={`Delete ${t.name}`}
                    onClick={() => {
                      if (confirm(`Delete ${t.name}?${t.members > 0 ? " Its people keep no team." : ""}`)) {
                        pending.change(saved, { name: saved.name, categoryIds: saved.categoryIds });
                        act(t.id, () => api(`/api/teams/${t.id}`, { method: "DELETE" }));
                      }
                    }}
                  >
                    <Icon name="trash" className="h-4 w-4" />
                  </button>
                </>
              )}
            </div>
            <Chips selected={t.categoryIds} onToggle={(id) => toggle(saved, id)} disabled={busy === t.id || pending.saving} />
          </li>
          );
        })}
      </ul>
      {/* Room for the save bar, so it never covers the last team. */}
      {pending.count > 0 && <div className="h-16" />}
      <SaveBar count={pending.count} progress={pending.progress} onSave={saveAll} onDiscard={pending.discard} />
    </div>
  );
}
