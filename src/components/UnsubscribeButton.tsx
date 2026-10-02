"use client";

import { useState } from "react";
import { api } from "@/lib/client";

/** The unsubscribe page's button: stops one kind of email for the account the link names. */
export function UnsubscribeButton({ u, k, label }: { u: string; k: string; label: string }) {
  const [state, setState] = useState<"ask" | "busy" | "done">("ask");
  const [error, setError] = useState<string | null>(null);
  if (state === "done") {
    return (
      <div className="space-y-4 text-sm">
        <p className="text-snow-soft">
          Done: you won&apos;t get <b className="text-snow">{label.toLowerCase()}</b> emails any more. You still see them in the app, and you can turn them back on
          from your Account page.
        </p>
        <a href="/account#email" className="btn-ghost w-full">
          Manage email
        </a>
      </div>
    );
  }
  return (
    <div className="space-y-4 text-sm">
      <p className="text-snow-soft">
        You&apos;ll stop getting <b className="text-snow">{label.toLowerCase()}</b> emails. They still show in the app. Account and security emails always come.
      </p>
      {error && <p className="error">{error}</p>}
      <button
        className="btn-gold w-full"
        disabled={state === "busy"}
        onClick={async () => {
          setState("busy");
          setError(null);
          try {
            await api("/api/email/unsubscribe", { method: "POST", json: { u, k } });
            setState("done");
          } catch (e) {
            setError(e instanceof Error ? e.message : "Could not do that.");
            setState("ask");
          }
        }}
      >
        {state === "busy" ? "Saving…" : `Stop ${label.toLowerCase()} emails`}
      </button>
      <a href="/account#email" className="btn-ghost w-full">
        Choose which emails I get
      </a>
    </div>
  );
}
