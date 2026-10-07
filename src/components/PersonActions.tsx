"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import { MenuButton, QuickAction, SectionHeading } from "./AppUI";
import { CopyButton } from "./CopyButton";
import { EditablePhoto, usePreview } from "./EditablePhoto";
import { Sheet } from "./Sheet";
import { STATUS_LABEL, type Status } from "@/lib/roles";
import type { PublicUser } from "@/lib/users";

const STATUS_CHOICES: Status[] = ["active", "suspended", "dismissed", "banned"];

/**
 * What can be done to a person, as on the app's person page: round actions under the banner (Message, QR code, Edit,
 * Resend invite), with the edit form and the QR code in sheets.
 */
export function PersonActions({
  person,
  editable,
  canChat,
  coordinators,
  qrSvg,
  qrLink,
}: {
  person: PublicUser;
  editable: boolean;
  canChat: boolean;
  coordinators: { id: string; name: string }[];
  qrSvg: string;
  qrLink: string;
}) {
  const router = useRouter();
  const [sheet, setSheet] = useState<"edit" | "qr" | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [f, setF] = useState({ name: person.name ?? "", dob: person.dob ?? "", phone: person.phone ?? "", teamName: person.teamName ?? "", parentId: person.parentId ?? "" });
  // A photo picked in the edit form waits for Save.
  const [newPhoto, setNewPhoto] = useState<File | null>(null);
  const preview = usePreview(newPhoto);

  const run = async (fn: () => Promise<void>, done?: string) => {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      await fn();
      if (done) setNote(done);
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  const openChat = () =>
    run(async () => {
      const r = await api<{ id: string }>("/api/conversations", { method: "POST", json: { memberId: person.id } });
      router.push(`/chats/${r.id}`);
    });

  const resend = () => run(() => api(`/api/users/${person.id}/invite`, { method: "POST" }).then(() => undefined), "Invite sent again.");

  const closeEdit = () => {
    setSheet(null);
    setNewPhoto(null);
  };

  const save = (e: React.FormEvent) => {
    e.preventDefault();
    run(async () => {
      const changes: Record<string, string> = {};
      if (f.name !== (person.name ?? "")) changes.name = f.name;
      if (f.dob !== (person.dob ?? "")) changes.dob = f.dob;
      if (f.phone !== (person.phone ?? "")) changes.phone = f.phone;
      if (f.teamName !== (person.teamName ?? "")) changes.teamName = f.teamName;
      if (f.parentId && f.parentId !== (person.parentId ?? "")) changes.parentId = f.parentId;
      if (Object.keys(changes).length > 0) await api(`/api/users/${person.id}`, { method: "PATCH", json: changes });
      if (newPhoto) {
        const form = new FormData();
        form.set("photo", await shrinkImage(newPhoto), "photo.jpg");
        await api(`/api/users/${person.id}/photo`, { method: "POST", body: form });
      }
      closeEdit();
    }, "Saved.");
  };

  const name = person.name ?? person.email;

  return (
    <>
      <div className="flex justify-center gap-2 py-5">
        {canChat && person.status === "active" && <QuickAction icon="chat" label="Message" onClick={openChat} disabled={busy} />}
        <QuickAction icon="scan" label="QR code" onClick={() => setSheet("qr")} />
        {editable && person.profileComplete && <QuickAction icon="edit" label="Edit" onClick={() => setSheet("edit")} disabled={busy} />}
        {person.status === "pending" && <QuickAction icon="mail" label="Resend invite" onClick={resend} disabled={busy} />}
      </div>

      {error && sheet !== "edit" && <p className="error mb-3">{error}</p>}
      {note && <p className="mb-3 rounded-xl border border-night-line px-3.5 py-2.5 text-sm text-snow-soft">{note}</p>}

      {sheet === "qr" && (
        <Sheet title={`${name}'s QR code`} onClose={() => setSheet(null)}>
          <div className="mx-auto w-56 rounded-2xl bg-white p-2" dangerouslySetInnerHTML={{ __html: qrSvg }} />
          <p className="mt-3 text-center text-sm text-snow-soft">Scanned at the gate, it shows who they are and their status.</p>
          <div className="mt-3 flex items-center justify-center gap-1 pb-2">
            <span className="font-mono text-sm tracking-wider text-snow-faint">{person.verifyCode}</span>
            <CopyButton value={qrLink} label="Copy the QR link" />
          </div>
        </Sheet>
      )}

      {sheet === "edit" && (
        <Sheet title={`Edit ${name}`} onClose={closeEdit}>
          <form id="person-edit" onSubmit={save} className="space-y-3 pb-2">
            {error && <p className="error">{error}</p>}
            <div className="flex items-center gap-4">
              <EditablePhoto src={preview ?? person.photoUrl} name={name} size={72} disabled={busy} onPicked={setNewPhoto} />
              <p className="text-xs text-snow-faint">Tap the photo to change it.</p>
            </div>
            <label className="block">
              <span className="label">Full name</span>
              <input className="input" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
            </label>
            <label className="block">
              <span className="label">Date of birth</span>
              <input className="input" type="date" value={f.dob} onChange={(e) => setF({ ...f, dob: e.target.value })} />
            </label>
            <label className="block">
              <span className="label">Contact number</span>
              <input className="input" type="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
            </label>
            {person.role === "team_manager" && (
              <label className="block">
                <span className="label">Team</span>
                <input className="input" value={f.teamName} onChange={(e) => setF({ ...f, teamName: e.target.value })} />
              </label>
            )}
            {coordinators.length > 0 && (
              <label className="block">
                <span className="label">Coordinator</span>
                <select className="input" value={f.parentId} onChange={(e) => setF({ ...f, parentId: e.target.value })}>
                  {coordinators.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <button className="btn-gold w-full py-3" disabled={busy}>
              {busy ? "Saving…" : "Save"}
            </button>
          </form>
        </Sheet>
      )}

    </>
  );
}

/** A person's status, for their manager or an admin: chips once they've joined, Dismiss or Ban while invited. */
export function PersonStatus({ person }: { person: PublicUser }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setStatus = async (status: Status) => {
    if (status !== "active" && !confirm(`Set ${person.name ?? person.email} to ${STATUS_LABEL[status].toLowerCase()}? They will be signed out.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/api/users/${person.id}`, { method: "PATCH", json: { status } });
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {error && <p className="error mt-3">{error}</p>}
      {person.status !== "pending" && (
        <section>
          <SectionHeading>Status</SectionHeading>
          <div className="flex flex-wrap gap-2 px-2 py-2">
            {STATUS_CHOICES.map((s) => (
              <button
                key={s}
                className={`chip ${person.status === s ? "border-gold bg-gold text-ink" : "hover:border-snow/40"}`}
                onClick={() => person.status !== s && setStatus(s)}
                disabled={busy}
              >
                {STATUS_LABEL[s]}
              </button>
            ))}
          </div>
        </section>
      )}
      {person.status === "pending" && (
        <section>
          <SectionHeading>Invite</SectionHeading>
          <MenuButton icon="close" title="Dismiss" hint="Turn down the invite: it can no longer be used" danger onClick={() => setStatus("dismissed")} disabled={busy} />
          <MenuButton icon="lock" title="Ban" hint="Turn down the invite and keep them out" danger onClick={() => setStatus("banned")} disabled={busy} />
        </section>
      )}
    </>
  );
}
