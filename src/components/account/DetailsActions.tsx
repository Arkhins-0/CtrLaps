"use client";

import { useState } from "react";
import { api } from "@/lib/client";
import { ChangePassword } from "../AccountActions";
import { Icon, type IconName } from "../Icon";
import { ProfileEditor } from "../ProfileEditor";
import { Sheet } from "../Sheet";

type Profile = Parameters<typeof ProfileEditor>[0]["profile"];

function ActionRow({ icon, title, hint, onClick }: { icon: IconName; title: string; hint: string; onClick: () => void }) {
  return (
    <button type="button" className="flex w-full items-center gap-5 rounded-xl px-2 py-3.5 text-left transition-colors hover:bg-snow/5" onClick={onClick}>
      <Icon name={icon} className="h-6 w-6 shrink-0 text-gold" />
      <span className="min-w-0 flex-1">
        <span className="block font-bold">{title}</span>
        <span className="block truncate text-sm text-snow-soft">{hint}</span>
      </span>
    </button>
  );
}

/** Account details' actions, as the app's rows: each opens its form in a sheet. */
export function DetailsActions({ email, profile }: { email: string; profile: Profile | null }) {
  const [sheet, setSheet] = useState<"edit" | "password" | "forgot" | null>(null);
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const forgot = async () => {
    setBusy(true);
    setError(null);
    try {
      await api("/api/auth/forgot", { method: "POST", json: { email } });
      setSent(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not send.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="mt-4">
      {profile && <ActionRow icon="edit" title="Edit details" hint="Name, photo, date of birth, contact and email" onClick={() => setSheet("edit")} />}
      <ActionRow icon="lock" title="Change password" hint="Other devices are signed out" onClick={() => setSheet("password")} />
      <ActionRow icon="refresh" title="Forgot password" hint={`Get a link at ${email}`} onClick={() => setSheet("forgot")} />
      {sheet === "edit" && profile && (
        <Sheet title="Edit details" onClose={() => setSheet(null)}>
          <ProfileEditor profile={profile} />
        </Sheet>
      )}
      {sheet === "password" && (
        <Sheet title="Change password" onClose={() => setSheet(null)}>
          <ChangePassword onDone={() => setSheet(null)} />
        </Sheet>
      )}
      {sheet === "forgot" && (
        <Sheet title="Forgot password" onClose={() => setSheet(null)}>
          <div className="space-y-4 pb-2">
            <p className="text-sm text-snow-soft">
              {sent ? `A link to choose a new password is on its way to ${email}. It works for 2 hours.` : `Don't know your current password? Get a link at ${email} to choose a new one.`}
            </p>
            {error && <p className="error">{error}</p>}
            {!sent && (
              <button type="button" className="btn-gold w-full py-3" disabled={busy} onClick={forgot}>
                {busy ? "Sending…" : "Email me a link"}
              </button>
            )}
          </div>
        </Sheet>
      )}
    </div>
  );
}
