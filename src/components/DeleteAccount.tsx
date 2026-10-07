"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { PasswordInput } from "./PasswordInput";

const longDate = (iso: string) => new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });

/** What deleting does, in a few lines (the Privacy Policy has the rest). */
function WhatHappens() {
  return (
    <ul className="list-disc space-y-1 pl-5 text-sm text-snow-soft">
      <li>You are signed out everywhere now. The account is deleted after 7 days; signing in again before then cancels it.</li>
      <li>Your profile, photo, email choices, follows, votes and support tickets are erased.</li>
      <li>Messages, photos and documents you sent in chats and groups stay for the people you sent them to, from &ldquo;Deleted user&rdquo;.</li>
      <li>Published race results keep the name as it was published.</li>
    </ul>
  );
}

/** Account page: delete my own account (password, and DELETE typed out). */
export function DeleteMyAccount() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/me/delete", { method: "POST", json: { password, confirm } });
      router.replace("/login?deleted=1");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete.");
      setBusy(false);
    }
  };

  return (
    <section className="card space-y-3" id="delete">
      <p className="section-title">Delete account</p>
      {!open ? (
        <div className="flex flex-wrap items-center gap-3">
          <p className="flex-1 text-sm text-snow-soft">Delete your account and the details we hold about you.</p>
          <button className="btn-ghost px-4 py-1.5 text-xs text-danger" onClick={() => setOpen(true)}>
            Delete my account
          </button>
        </div>
      ) : (
        <form onSubmit={submit} className="max-w-md space-y-3">
          <WhatHappens />
          {error && <p className="error">{error}</p>}
          <label className="block">
            <span className="label">Password</span>
            <PasswordInput className="input" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <label className="block">
            <span className="label">Type DELETE to confirm</span>
            <input className="input" autoComplete="off" required value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </label>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button className="btn-danger px-4 py-1.5 text-xs" disabled={busy || confirm.trim().toUpperCase() !== "DELETE" || !password}>
              {busy ? "Deleting…" : "Delete my account"}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

/** Person page, for a developer: delete this account on the person's request, or withdraw a waiting deletion. */
export function DeletePersonAccount({ personId, name, dueAt }: { personId: string; name: string; dueAt: string | null }) {
  const router = useRouter();
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async (method: "POST" | "DELETE") => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/users/${personId}/deletion`, { method });
      setAsking(false);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not do that.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card space-y-3">
      <p className="label">Delete account</p>
      {error && <p className="error">{error}</p>}
      {dueAt ? (
        <div className="flex flex-wrap items-center gap-3">
          <p className="flex-1 text-sm text-snow-soft">Will be deleted on {longDate(dueAt)}, unless they sign in before then.</p>
          <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => act("DELETE")} disabled={busy}>
            Cancel deletion
          </button>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-3">
          <p className="flex-1 text-sm text-snow-soft">Only when they ask for it. They get an email and 7 days to change their mind.</p>
          <button className="btn-ghost px-4 py-1.5 text-xs text-danger" onClick={() => setAsking(true)} disabled={busy}>
            Delete account
          </button>
        </div>
      )}
      {asking && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 p-4 sm:items-center" role="dialog" aria-modal="true" onClick={() => setAsking(false)}>
          <div className="card w-full max-w-md space-y-3" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-semibold">Delete {name}&rsquo;s account?</h2>
            <WhatHappens />
            <div className="flex justify-end gap-2 pt-1">
              <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setAsking(false)} autoFocus>
                Cancel
              </button>
              <button className="btn-danger px-4 py-1.5 text-xs" onClick={() => act("POST")} disabled={busy}>
                {busy ? "Deleting…" : "Delete account"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
