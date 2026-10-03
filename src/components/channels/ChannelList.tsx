"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { CategoryChannel } from "@/lib/categoryChannels";
import type { ChannelManager, ChannelSeason } from "@/lib/channels";
import { WhenLabel } from "../ChatList";
import { BellIcon } from "./BellIcon";
import { Dialog } from "../groups/Dialog";
import { PeoplePicker } from "../groups/PeoplePicker";
import { pickPerson, type PickPerson } from "../groups/people";

/**
 * The broadcast channels: one per race weekend, listed season by season
 * with the current season on top. A row opens its weekend. An admin names the coordinators who manage a channel from
 * here. Above them, every race category's channel (everyone sees them all). A muted channel's count is drawn inverted.
 */
export function ChannelList({ initial, categories = [], canManage }: { initial: ChannelSeason[]; categories?: CategoryChannel[]; canManage: boolean }) {
  const [seasons, setSeasons] = useState(initial);
  // Whose managers are being picked: a weekend's channel or a category's (its name and managers API).
  const [managing, setManaging] = useState<{ name: string; url: string; weekendId?: string } | null>(null);
  const shown = seasons.filter((s) => s.weekends.length > 0);

  const saved = (weekendId: string, managers: ChannelManager[]) =>
    setSeasons((all) => all.map((s) => ({ ...s, weekends: s.weekends.map((w) => (w.id === weekendId ? { ...w, managers } : w)) })));

  return (
    <div className="-mx-4 min-h-0 flex-1 overflow-y-auto pb-4 sm:-mx-6 lg:mx-0">
      {categories.length > 0 && (
        <section className="mb-3">
          <div className="px-4 py-2 sm:px-6 lg:px-3">
            <p className="section-title">Categories</p>
          </div>
          {categories.map((c, i) => (
            <div key={c.id}>
              {i > 0 && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
              <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3">
                <Link href={`/c/${c.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                  <span
                    className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] text-[12px] font-bold tracking-wide"
                    style={{ color: c.color, backgroundColor: `${c.color}26` }}
                    aria-hidden
                  >
                    {c.code.slice(0, 5)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium">
                      {c.name}
                      {c.muted && <BellIcon muted className="ml-1.5 inline h-3.5 w-3.5 text-snow-faint" />}
                    </span>
                    <span className={`block truncate text-[13px] ${c.unread > 0 ? "text-snow" : "text-snow-faint"}`}>{c.lastMessage ?? "No posts yet"}</span>
                  </span>
                </Link>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  {c.lastMessageAt && (
                    <span className={`text-[11px] ${c.unread > 0 && !c.muted ? "text-gold" : "text-snow-faint"}`}>
                      <WhenLabel iso={c.lastMessageAt} />
                    </span>
                  )}
                  {c.unread > 0 && <span className={c.muted ? "badge-muted" : "badge"}>{c.unread > 99 ? "99+" : c.unread}</span>}
                  {canManage && (
                    <button className="rounded px-1 text-[11px] font-semibold text-gold hover:underline" onClick={() => setManaging({ name: c.name, url: `/api/categories/${c.id}/managers` })}>
                      Managers
                    </button>
                  )}
                </span>
              </div>
            </div>
          ))}
        </section>
      )}
      {shown.length === 0 && <p className="px-4 py-3 text-sm text-snow-faint sm:px-6 lg:px-3">No race weekends yet.</p>}
      {shown.map((s) => (
        <section key={s.id} className="mb-3">
          <div className="flex items-center gap-2 px-4 py-2 sm:px-6 lg:px-3">
            <p className="section-title flex-1">{s.name}</p>
            {s.current ? (
              <span className="chip border-gold/50 px-2 py-0 text-[10px] text-gold">Current</span>
            ) : s.status === "archived" ? (
              <span className="chip px-2 py-0 text-[10px] text-snow-faint">Archived</span>
            ) : null}
          </div>
          {s.weekends.map((w, i) => (
            <div key={w.id}>
              {i > 0 && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
              <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3">
                <Link href={`/w/${w.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                  <span
                    className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] text-xl ${w.channelOpen ? "bg-gold/15" : "border border-night-line bg-night-panel"}`}
                    aria-hidden
                  >
                    📣
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium">
                      {w.name}
                      {w.muted && <BellIcon muted className="ml-1.5 inline h-3.5 w-3.5 text-snow-faint" />}
                    </span>
                    <span className={`block truncate text-[13px] ${w.unread > 0 ? "text-snow" : "text-snow-faint"}`}>
                      {w.lastMessage ?? `${w.startsOn} → ${w.endsOn}${w.channelOpen ? "" : " · closed"}`}
                    </span>
                    {w.managers.length > 0 && (
                      <span className="block truncate text-[11px] text-snow-faint">Managed by {w.managers.map((m) => m.name).join(", ")}</span>
                    )}
                  </span>
                </Link>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  {w.lastMessageAt && (
                    <span className={`text-[11px] ${w.unread > 0 && !w.muted ? "text-gold" : "text-snow-faint"}`}>
                      <WhenLabel iso={w.lastMessageAt} />
                    </span>
                  )}
                  {w.unread > 0 && <span className={w.muted ? "badge-muted" : "badge"}>{w.unread > 99 ? "99+" : w.unread}</span>}
                  {canManage && (
                    <button className="rounded px-1 text-[11px] font-semibold text-gold hover:underline" onClick={() => setManaging({ name: w.name, url: `/api/weekends/${w.id}/managers`, weekendId: w.id })}>
                      Managers
                    </button>
                  )}
                </span>
              </div>
            </div>
          ))}
        </section>
      ))}
      {managing && (
        <ManagersDialog
          name={managing.name}
          url={managing.url}
          onClose={() => setManaging(null)}
          onSaved={(managers) => {
            if (managing.weekendId) saved(managing.weekendId, managers);
            setManaging(null);
          }}
        />
      )}
    </div>
  );
}

/** Admin: tick the coordinators who manage a weekend's or a category's channel (they post there; admins post everywhere). */
function ManagersDialog({ name, url, onClose, onSaved }: { name: string; url: string; onClose: () => void; onSaved: (managers: ChannelManager[]) => void }) {
  type Row = { id: string; name: string | null; email: string; role: string; status?: string; roleLabel: string; teamName?: string | null; photoUrl: string | null };
  const [people, setPeople] = useState<PickPerson[] | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<{ managers: Row[]; candidates: Row[] }>(url)
      .then((r) => {
        const eligible = r.candidates.map((p) => pickPerson({ ...p, teamName: p.teamName ?? null }));
        // Someone already managing but no longer a coordinator stays tickable, so saving does not drop them unseen.
        const extra = r.managers.filter((m) => !eligible.some((p) => p.id === m.id)).map((m) => pickPerson({ ...m, teamName: null }));
        setPeople([...extra, ...eligible]);
        setPicked(new Set(r.managers.map((m) => m.id)));
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load people."));
  }, [url]);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ managers: ChannelManager[] }>(url, { method: "PUT", json: { userIds: Array.from(picked) } });
      onSaved(r.managers);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <Dialog title={`Managers of ${name}`} onClose={onClose}>
      <p className="-mt-2 text-sm text-snow-soft">Coordinators who post in this channel. Admins post in every channel.</p>
      <input className="input" placeholder="Search people" value={filter} onChange={(e) => setFilter(e.target.value)} />
      {error && <p className="error">{error}</p>}
      <div className="min-h-0 flex-1 overflow-y-auto">
        {!people && !error && <p className="py-3 text-sm text-snow-faint">Loading…</p>}
        {people && people.length === 0 && <p className="py-3 text-sm text-snow-faint">There are no coordinators to pick.</p>}
        {people && people.length > 0 && <PeoplePicker people={people} picked={picked} filter={filter} onChange={setPicked} disabled={busy} />}
      </div>
      <button className="btn-gold w-full" onClick={save} disabled={busy || !people}>
        {busy ? "Saving…" : "Save"}
      </button>
    </Dialog>
  );
}
