"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { TicketView as View } from "@/lib/support";
import { MessageComposer, post } from "../MessageComposer";
import { MessageItem } from "../MessageList";

/**
 * A ticket: what was asked, then its chat with Support, oldest first, and the box to write in while it is open.
 * Its owner or a developer closes it; its owner reopens it within 2 days, a developer any time.
 */
export function TicketView({ initial }: { initial: View }) {
  const [v, setV] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const end = useRef<HTMLDivElement>(null);
  const t = v.ticket;
  const base = `/api/support/tickets/${t.id}`;

  const reload = async () => {
    try {
      setV(await api<View>(base));
    } catch {
      // Next time.
    }
  };
  useEffect(() => {
    const timer = setInterval(reload, 10_000);
    const onPush = () => reload();
    window.addEventListener("ctrlaps:push", onPush);
    return () => {
      clearInterval(timer);
      window.removeEventListener("ctrlaps:push", onPush);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [base]);
  useEffect(() => end.current?.scrollIntoView({ block: "end" }), [v.messages.length]);

  const setStatus = async (status: "open" | "closed") => {
    if (status === "closed" && !confirm(v.isDev ? "Close this ticket?" : "Close this ticket? You can reopen it within 2 days.")) return;
    setBusy(true);
    setError(null);
    try {
      setV(await api<View>(base, { method: "PATCH", json: { status } }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change it.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <section className="card space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm text-gold">{t.label}</span>
          <span className={`chip px-2 py-0 text-[10px] ${t.status === "open" ? "border-gold/40 text-gold" : "text-snow-faint"}`}>{t.status === "open" ? "Open" : "Closed"}</span>
          <span className="chip px-2 py-0 text-[10px]">{t.category}</span>
          <span className="ml-auto text-xs text-snow-faint">{new Date(t.createdAt).toLocaleString([], { dateStyle: "medium", timeStyle: "short", hour12: true })}</span>
        </div>
        <h1 className="text-lg font-semibold">{t.subject}</h1>
        {v.isDev && (
          <p className="text-xs text-snow-soft">
            {t.name} · {t.email}
            {t.phone ? ` · ${t.phone}` : ""}
            {!t.userId && " · no account (replies go by email)"}
          </p>
        )}
        {/* The details open the chat below (with any files) when it was raised signed in: not twice. */}
        {!v.messages.some((m) => m.body.trim() === t.details.trim()) && <p className="whitespace-pre-wrap text-sm text-snow-soft">{t.details}</p>}
        <div className="flex flex-wrap items-center gap-2">
          {v.canClose && <button className="btn-ghost px-4 py-1.5 text-xs" disabled={busy} onClick={() => setStatus("closed")}>Close ticket</button>}
          {v.canReopen && <button className="btn-gold px-4 py-1.5 text-xs" disabled={busy} onClick={() => setStatus("open")}>Reopen ticket</button>}
          {t.status === "closed" && !v.canReopen && !v.isDev && <p className="text-xs text-snow-faint">Closed more than 2 days ago. Raise a new ticket if you still need help.</p>}
          {t.status === "closed" && v.canReopen && !v.isDev && v.reopenUntil && (
            <p className="text-xs text-snow-faint">You can reopen it until {new Date(v.reopenUntil).toLocaleString([], { dateStyle: "medium", timeStyle: "short", hour12: true })}.</p>
          )}
        </div>
        {error && <p className="error">{error}</p>}
      </section>

      <div className="space-y-2">
        {v.messages.map((m) =>
          m.event ? (
            <p key={m.id} className="mx-auto w-fit rounded-full bg-night-panel px-3 py-1 text-center text-xs text-snow-faint">{m.event}</p>
          ) : (
            <MessageItem key={m.id} m={m} inPlace />
          ),
        )}
        {v.messages.length === 0 && <p className="text-center text-xs text-snow-faint">{v.isDev ? "No replies yet." : "Support will reply here."}</p>}
        <div ref={end} />
      </div>

      {v.canReply && (
        <MessageComposer
          placeholder={v.isDev ? "Reply as Support" : "Write to Support"}
          urgentOption={false}
          send={async (draft) => {
            await post(base, draft);
            await reload();
          }}
        />
      )}
    </div>
  );
}
