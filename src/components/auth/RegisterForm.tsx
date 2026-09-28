"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/client";

/** Step one of registering: the email, which gets a link to confirm it. */
export function RegisterForm() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/auth/register", { method: "POST", json: { email } });
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send the link.");
    } finally {
      setBusy(false);
    }
  };

  if (sent) {
    return (
      <div className="space-y-4 text-sm text-snow-soft">
        <p>
          We sent a link to <span className="font-semibold text-snow">{email}</span>. Open it to confirm your email and create your account. It works for 24 hours.
        </p>
        <p className="text-xs text-snow-faint">Nothing arrived? Check your spam folder, or try again in a few minutes.</p>
        <Link href="/" className="btn-ghost w-full">
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && <p className="error">{error}</p>}
      <p className="text-sm text-snow-soft">Enter your email. We&apos;ll send a link to confirm it before your account is created.</p>
      <div>
        <label className="label" htmlFor="email">
          Email
        </label>
        <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Sending…" : "Send the link"}
      </button>
      <Link href="/" className="block text-center text-xs text-snow-faint hover:text-snow">
        Already have an account? Sign in
      </Link>
    </form>
  );
}
