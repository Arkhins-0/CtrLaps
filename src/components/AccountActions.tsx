"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { Scanner } from "./Scanner";
import { PasswordInput } from "./PasswordInput";

/** The account page's tools: scan a QR / type a code, change password, sign out. */
export function AccountActions({ appVersion, isDev = false }: { appVersion: string | null; isDev?: boolean }) {
  const router = useRouter();
  const [panel, setPanel] = useState<"none" | "scan" | "password">("none");
  const [confirmOut, setConfirmOut] = useState(false);

  const signOut = async () => {
    await api("/api/auth/logout", { method: "POST", json: {} }).catch(() => null);
    router.replace("/");
    router.refresh();
  };

  return (
    <section className="card space-y-3 self-start">
      <p className="section-title">Tools</p>
      <div className="flex flex-wrap gap-2">
        <button className={panel === "scan" ? "btn-gold px-4 py-1.5 text-xs" : "btn-ghost px-4 py-1.5 text-xs"} onClick={() => setPanel(panel === "scan" ? "none" : "scan")}>
          Scan a QR code
        </button>
        <button
          className={panel === "password" ? "btn-gold px-4 py-1.5 text-xs" : "btn-ghost px-4 py-1.5 text-xs"}
          onClick={() => setPanel(panel === "password" ? "none" : "password")}
        >
          Change password
        </button>
        <a href="/archive" className="btn-ghost px-4 py-1.5 text-xs">
          Archive
        </a>
        <a href="/support" className="btn-ghost px-4 py-1.5 text-xs">
          Support
        </a>
        {isDev && (
          <a href="/activity" className="btn-ghost px-4 py-1.5 text-xs">
            Activity log
          </a>
        )}
        <button className="btn-ghost ml-auto px-4 py-1.5 text-xs text-danger" onClick={() => setConfirmOut(true)}>
          Sign out
        </button>
      </div>
      {confirmOut && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 p-4 sm:items-center" role="dialog" aria-modal="true" onClick={() => setConfirmOut(false)}>
          <div className="card w-full max-w-sm space-y-3" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-semibold">Sign out?</h2>
            <p className="text-sm text-snow-soft">You can sign in again any time with your email and password.</p>
            <div className="flex justify-end gap-2 pt-1">
              <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setConfirmOut(false)} autoFocus>
                Cancel
              </button>
              <button className="btn-danger px-4 py-1.5 text-xs" onClick={signOut}>
                Sign out
              </button>
            </div>
          </div>
        </div>
      )}
      {panel === "scan" && <Scanner />}
      {panel === "password" && <ChangePassword onDone={() => setPanel("none")} />}
      <p className="text-xs text-snow-faint">
        <a href="/download" className="hover:text-snow">
          Open the Android app{appVersion ? ` (v${appVersion})` : ""}
        </a>
      </p>
    </section>
  );
}

function ChangePassword({ onDone }: { onDone: () => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (next !== again) return setError("The new passwords do not match.");
    setBusy(true);
    setError(null);
    try {
      await api("/api/auth/password", { method: "POST", json: { current, next } });
      setDone(true);
      setTimeout(onDone, 1500);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not change.");
    } finally {
      setBusy(false);
    }
  };

  if (done) return <p className="card text-sm text-snow-soft">Password changed. Other devices are signed out.</p>;

  return (
    <form onSubmit={submit} className="card max-w-sm space-y-3">
      {error && <p className="error">{error}</p>}
      <label className="block">
        <span className="label">Current password</span>
        <PasswordInput className="input" autoComplete="current-password" required value={current} onChange={(e) => setCurrent(e.target.value)} />
      </label>
      <label className="block">
        <span className="label">New password</span>
        <PasswordInput className="input" autoComplete="new-password" minLength={8} required value={next} onChange={(e) => setNext(e.target.value)} />
      </label>
      <label className="block">
        <span className="label">Repeat new password</span>
        <PasswordInput className="input" autoComplete="new-password" required value={again} onChange={(e) => setAgain(e.target.value)} />
      </label>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Saving…" : "Change password"}
      </button>
    </form>
  );
}
