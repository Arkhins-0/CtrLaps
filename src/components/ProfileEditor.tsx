"use client";

/* eslint-disable @next/next/no-img-element */
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import { PhotoCropDialog } from "./PhotoCropDialog";

type Profile = { name: string; dob: string; phone: string; email: string; photoUrl: string | null; admin?: boolean };

/** For someone with no role yet, or an admin: change their own details, and their email through a link to the new address. */
export function ProfileEditor({ profile }: { profile: Profile }) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [f, setF] = useState({ name: profile.name, dob: profile.dob, phone: profile.phone });
  const [photo, setPhoto] = useState<File | null>(null);
  const [cropping, setCropping] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [emailSentTo, setEmailSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!photo) return setPreview(null);
    const url = URL.createObjectURL(photo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.set("name", f.name);
      form.set("dob", f.dob);
      form.set("phone", f.phone);
      if (photo) form.set("photo", await shrinkImage(photo), "photo.jpg");
      await api("/api/me/details", { method: "POST", body: form });
      setEditing(false);
      setPhoto(null);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  };

  const changeEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setEmailError(null);
    try {
      await api("/api/me/email", { method: "POST", json: { email } });
      setEmailSentTo(email.trim().toLowerCase());
      setEmail("");
    } catch (err) {
      setEmailError(err instanceof Error ? err.message : "Could not send the link.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card space-y-5">
      {cropping && <PhotoCropDialog file={cropping} onCancel={() => setCropping(null)} onDone={(f) => { setCropping(null); setPhoto(f); }} />}
      {editing ? (
        <form onSubmit={save} className="space-y-4">
          <h2 className="font-semibold">Edit profile</h2>
          {error && <p className="error">{error}</p>}
          <label className="flex cursor-pointer items-center gap-4">
            {preview || profile.photoUrl ? (
              <img src={preview ?? profile.photoUrl!} alt="" className="h-20 w-20 rounded-full border border-night-line object-cover" />
            ) : (
              <span className="flex h-20 w-20 items-center justify-center rounded-full border border-dashed border-snow/30 text-xs text-snow-faint">Photo</span>
            )}
            <span className="btn-ghost text-xs">Change photo</span>
            <input type="file" accept="image/*" className="hidden" disabled={busy} onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ""; if (f) setCropping(f); }} />
          </label>
          <div>
            <label className="label" htmlFor="pe-name">Full name</label>
            <input id="pe-name" className="input" required minLength={2} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="pe-dob">Date of birth</label>
            <input id="pe-dob" className="input" type="date" required max={new Date().toISOString().slice(0, 10)} value={f.dob} onChange={(e) => setF({ ...f, dob: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="pe-phone">Contact number</label>
            <input id="pe-phone" className="input" type="tel" required value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
          </div>
          <div className="flex gap-2">
            <button className="btn-gold flex-1" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
            <button type="button" className="btn-ghost" disabled={busy} onClick={() => { setEditing(false); setPhoto(null); setError(null); setF({ name: profile.name, dob: profile.dob, phone: profile.phone }); }}>
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm text-snow-soft">{profile.admin ? "Your name, contact, date of birth and photo." : "You can change your details until an organiser gives you a role."}</p>
          <button className="btn-ghost shrink-0 text-xs" onClick={() => setEditing(true)}>Edit profile</button>
        </div>
      )}

      <form onSubmit={changeEmail} className="space-y-3 border-t border-night-line pt-4">
        <h2 className="font-semibold">Change email</h2>
        <p className="text-xs text-snow-faint">We send a link to the new address. Your email changes only when you open it.</p>
        {emailSentTo && <p className="rounded-xl border border-night-line px-3.5 py-2.5 text-sm text-snow-soft">A link is on its way to {emailSentTo}. It works for 24 hours.</p>}
        {emailError && <p className="error">{emailError}</p>}
        <div className="flex gap-2">
          <input className="input flex-1" type="email" required placeholder="New email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <button className="btn-gold shrink-0" disabled={busy || !email.trim()}>Send link</button>
        </div>
      </form>
    </section>
  );
}
