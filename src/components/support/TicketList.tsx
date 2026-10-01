"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { TicketListItem } from "@/lib/support";

type Status = "open" | "closed" | "all";

/** Tickets with Open · Closed · All: your own, or (a developer) everyone's, with who raised each. */
export function TicketList({ isDev }: { isDev: boolean }) {
  const [status, setStatus] = useState<Status>("open");
  const [tickets, setTickets] = useState<TicketListItem[] | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    const load = () =>
      api<{ tickets: TicketListItem[] }>(`/api/support/tickets?status=${status}`)
        .then((r) => live && (setTickets(r.tickets), setError(null)))
        .catch((e) => live && setError(e instanceof Error ? e.message : "Could not load the tickets."));
    load();
    const timer = setInterval(load, 20_000);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [status]);

  const q = query.trim().toLowerCase();
  const shown = (tickets ?? []).filter((t) => !q || `${t.label} ${t.subject} ${t.category} ${t.name} ${t.email}`.toLowerCase().includes(q));

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        {(["open", "closed", "all"] as Status[]).map((s) => (
          <button key={s} className={status === s ? "btn-gold px-4 py-1.5 text-xs" : "btn-ghost px-4 py-1.5 text-xs"} onClick={() => setStatus(s)}>
            {s === "open" ? "Open" : s === "closed" ? "Closed" : "All"}
          </button>
        ))}
        {isDev && <input className="input ml-auto w-full py-1.5 text-sm sm:w-56" type="search" placeholder="Search tickets" value={query} onChange={(e) => setQuery(e.target.value)} />}
      </div>
      {error && <p className="error">{error}</p>}
      {tickets === null && !error && <p className="text-sm text-snow-faint">Loading…</p>}
      {tickets !== null && shown.length === 0 && (
        <p className="card text-sm text-snow-faint">{status === "open" ? "No open tickets." : status === "closed" ? "No closed tickets." : "No tickets yet."}</p>
      )}
      {shown.map((t) => (
        <Link key={t.id} href={`/support/tickets/${t.id}`} className="row border border-night-line bg-night-panel/60 hover:border-gold/40">
          <span className="min-w-0 flex-1">
            <span className="flex items-baseline gap-2">
              <span className="shrink-0 font-mono text-xs text-gold">{t.label}</span>
              <span className="truncate text-sm font-medium">{t.subject}</span>
            </span>
            <span className="block truncate text-xs text-snow-faint">
              {isDev ? `${t.name} · ` : ""}
              {t.category}
              {t.lastMessage ? ` · ${t.lastMessage}` : ""}
            </span>
          </span>
          <span className="flex shrink-0 flex-col items-end gap-1">
            <span className={`chip px-2 py-0 text-[10px] ${t.status === "open" ? "border-gold/40 text-gold" : "text-snow-faint"}`}>{t.status === "open" ? "Open" : "Closed"}</span>
            {t.unread > 0 && <span className="rounded-full bg-gold px-1.5 text-[10px] font-bold text-night">{t.unread}</span>}
          </span>
        </Link>
      ))}
    </div>
  );
}
