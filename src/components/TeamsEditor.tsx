"use client";

import Link from "next/link";
import { useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import type { Category } from "@/lib/categories";
import { usePendingEdits } from "@/lib/pendingEdits";
import type { Team } from "@/lib/teams";
import { GroupTitle, SQUARE_BUTTON, SearchPill } from "./AppUI";
import { Avatar } from "./Avatar";
import { Icon } from "./Icon";
import { Sheet } from "./Sheet";
import { SaveBar } from "./SaveBar";
import { contrastText } from "@/lib/colors";

/**
 * Teams and the race categories each is entered in this season. Tapping categories and renaming wait, so many teams
 * can be set up and saved together from the bar at the bottom; adding or deleting a team, and its photo, happen at once.
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

  /** Send the photo (or null to take it away); the row shows what the server kept. A failure is the photo sheet's to show. */
  const setPhoto = async (team: Team, photo: File | null) => {
    let r: { photoUrl: string | null };
    if (photo) {
      const form = new FormData();
      form.set("photo", await shrinkImage(photo), "photo.jpg");
      r = await api(`/api/teams/${team.id}/photo`, { method: "POST", body: form });
    } else {
      r = await api(`/api/teams/${team.id}/photo`, { method: "DELETE" });
    }
    setTeams((ts) => ts.map((x) => (x.id === team.id ? { ...x, photoUrl: r.photoUrl } : x)));
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
      <div className="flex items-center gap-2.5">
        <SearchPill query={search} onChange={setSearch} placeholder="Search teams" />
        <button type="button" className={SQUARE_BUTTON} aria-label="New team" onClick={() => setAdding({ name: "", categoryIds: [] })}>
          <Icon name="plus" className="h-6 w-6" />
        </button>
      </div>
      {adding && (
        <Sheet title="New team" onClose={() => setAdding(null)}>
        <form
          className="space-y-3 pb-2"
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
          {error && <p className="error">{error}</p>}
          <button className="btn-gold w-full py-3" disabled={busy === "new"}>
            {busy === "new" ? "Saving…" : "Add team"}
          </button>
        </form>
        </Sheet>
      )}
      {teams.length === 0 && <p className="text-sm text-snow-faint">No teams yet. Tap + to add the first.</p>}
      {teams.length > 0 && <GroupTitle title="Teams" count={shown.length} />}
      <ul className="grid grid-cols-1 gap-x-4 lg:grid-cols-2">
        {shown.map((saved) => {
          const t = pending.view(saved);
          return (
          <li key={t.id} className="space-y-2 px-1 py-2.5">
            <div className="flex items-center gap-3.5">
              {/* Its logo or its car: tapped, it pulls up with Change and Remove, or picks a first one. */}
              <Avatar src={t.photoUrl} name={t.name} size={46} edit={{ upload: (f) => setPhoto(saved, f), remove: () => setPhoto(saved, null) }} />
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
                  <span className="min-w-0 flex-1">
                    <Link href={`/teams/${t.id}`} className="block truncate font-bold hover:text-gold">
                      {t.name}
                    </Link>
                    <span className="block text-sm text-snow-soft">{t.members === 1 ? "1 person" : `${t.members} people`}</span>
                  </span>
                  <button className="btn-icon text-gold" aria-label={`Rename ${t.name}`} onClick={() => setRenaming({ id: t.id, name: t.name })}>
                    <Icon name="edit" className="h-5 w-5" />
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
                    <Icon name="trash" className="h-5 w-5" />
                  </button>
                </>
              )}
            </div>
            {/* In line with the name, under it. */}
            <div className="space-y-1.5 pl-[60px]">
              <Chips selected={t.categoryIds} onToggle={(id) => toggle(saved, id)} disabled={busy === t.id || pending.saving} />
              {pending.isChanged(t.id) && <p className="text-sm text-gold">Not saved</p>}
            </div>
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
