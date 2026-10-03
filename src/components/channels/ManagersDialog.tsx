"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { ChannelManager } from "@/lib/channels";
import { Dialog } from "../groups/Dialog";
import { PeoplePicker } from "../groups/PeoplePicker";
import { pickPerson, type PickPerson } from "../groups/people";

/** Admin: tick the coordinators who manage a weekend's or a category's channel (they post there; admins post everywhere). */
export function ManagersDialog({ name, url, onClose, onSaved }: { name: string; url: string; onClose: () => void; onSaved: (managers: ChannelManager[]) => void }) {
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
