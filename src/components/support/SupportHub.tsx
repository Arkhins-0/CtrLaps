"use client";

import { useState } from "react";
import { FaqList, type Faq } from "./FaqList";
import { TicketForm } from "./TicketForm";
import { TicketList } from "./TicketList";

type Tab = "faqs" | "form" | "tickets";

/**
 * Support: the FAQs, the form to raise a ticket, and (signed in) the tickets — your own, or for a developer everyone's.
 */
export function SupportHub({
  faqs,
  prefill,
  signedIn,
  isDev = false,
  start = "faqs",
}: {
  faqs: Faq[];
  prefill: { name: string; email: string; phone: string };
  signedIn: boolean;
  isDev?: boolean;
  start?: Tab;
}) {
  const [tab, setTab] = useState<Tab>(start);
  const tabs: [Tab, string][] = [["faqs", "FAQs"], ["form", "Support form"], ...(signedIn ? ([["tickets", "Tickets"]] as [Tab, string][]) : [])];
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {tabs.map(([k, label]) => (
          <button key={k} className={tab === k ? "btn-gold px-4 py-1.5 text-xs" : "btn-ghost px-4 py-1.5 text-xs"} onClick={() => setTab(k)}>
            {label}
          </button>
        ))}
      </div>
      {tab === "faqs" && <FaqList initial={faqs} canEdit={isDev} />}
      {tab === "form" && <TicketForm prefill={prefill} signedIn={signedIn} />}
      {tab === "tickets" && <TicketList isDev={isDev} onRaise={() => setTab("form")} />}
    </div>
  );
}
