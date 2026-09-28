"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";

/** One button, so a mail scanner opening the link does not make the change by itself. */
export function ConfirmEmailForm({ token }: { token: string }) {
  const [email, setEmail] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<{ email: string }>(`/api/auth/email/${token}`).then((r) => setEmail(r.email)).catch((e) => setError(e.message));
  }, [token]);

  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/auth/email/${token}`, { method: "POST" });
      setDone(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change the email.");
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <div className="space-y-4 text-sm text-snow-soft">
        <p>
          Your account now uses <span className="font-semibold text-snow">{email}</span>. Sign in with it from now on.
        </p>
        <Link href="/home" className="btn-gold w-full">
          Continue
        </Link>
      </div>
    );
  }
  if (error) {
    return (
      <div className="space-y-4">
        <p className="error">{error}</p>
        <Link href="/" className="btn-ghost w-full">
          Back
        </Link>
      </div>
    );
  }
  if (!email) return <p className="text-sm text-snow-faint">Checking your link…</p>;
  return (
    <div className="space-y-4">
      <p className="text-sm text-snow-soft">
        Make <span className="font-semibold text-snow">{email}</span> the email of your account?
      </p>
      <button className="btn-gold w-full" disabled={busy} onClick={confirm}>
        {busy ? "Saving…" : "Confirm this email"}
      </button>
    </div>
  );
}
