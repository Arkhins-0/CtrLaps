"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import { PasswordForm } from "./PasswordForm";

/** Step two of registering: the email is confirmed; choose a password and the account is made. */
export function SignupForm({ token }: { token: string }) {
  const router = useRouter();
  const [email, setEmail] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<{ email: string }>(`/api/auth/register/${token}`).then((r) => setEmail(r.email)).catch((e) => setError(e.message));
  }, [token]);

  if (error) {
    return (
      <div className="space-y-4">
        <p className="error">{error}</p>
        <Link href="/register" className="btn-ghost w-full">
          Register again
        </Link>
      </div>
    );
  }
  if (!email) return <p className="text-sm text-snow-faint">Checking your link…</p>;

  return (
    <div className="space-y-5">
      <p className="text-sm text-snow-soft">
        <span className="font-semibold text-snow">{email}</span> is confirmed. Choose a password to create your account.
      </p>
      <PasswordForm
        label="Create account"
        agreement
        onSubmit={async (password) => {
          await api(`/api/auth/register/${token}`, { method: "POST", json: { password, acceptTerms: true } });
          router.replace("/onboarding");
          router.refresh();
        }}
      />
    </div>
  );
}
