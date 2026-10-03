"use client";

import { useState } from "react";
import type { MessageOut } from "@/lib/messages";

/** The line under the last unread post in a feed shown newest first: a thin red rule with NEW at its end. */
export function NewLine() {
  return (
    <div className="flex items-center" role="separator" aria-label="New posts above">
      <div className="h-px flex-1 bg-danger" />
      <span className="rounded-l bg-danger px-1.5 py-px text-[10px] font-bold leading-tight text-white">NEW</span>
    </div>
  );
}

/** The posts that were unread when the page first showed: decided once, so the line doesn't move or vanish. */
export function useNewIds(initial: MessageOut[]): Set<string> {
  const [ids] = useState(() => new Set(initial.filter((m) => !m.readAt && !m.mine).map((m) => m.id)));
  return ids;
}

/** In a list shown newest first: the index of the last post that is new, where the line goes under. */
export function lastNewIndex(list: MessageOut[], ids: Set<string>): number {
  for (let i = list.length - 1; i >= 0; i--) if (ids.has(list[i].id)) return i;
  return -1;
}

/** "Saturday, 3 October", in the reader's zone (so only once hydrated). */
export const dayOf = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });

/** A day's heading in a feed: a pill that sticks to the top while that day's posts scroll under it. */
export function DayHeader({ label }: { label: string }) {
  return (
    <div className="sticky top-0 z-10 -mx-1 flex justify-center bg-night/85 px-1 py-1.5 backdrop-blur">
      <span className="chip text-snow-faint">{label}</span>
    </div>
  );
}

/** A feed newest first, with a heading at each new day and the NEW line under the last unread post. */
export function Feed({ list, newIds, hydrated, render }: { list: MessageOut[]; newIds: Set<string>; hydrated: boolean; render: (m: MessageOut) => React.ReactNode }) {
  const last = lastNewIndex(list, newIds);
  const out: React.ReactNode[] = [];
  let day = "";
  list.forEach((m, i) => {
    const d = hydrated ? dayOf(m.createdAt) : "";
    if (d && d !== day) {
      day = d;
      out.push(<DayHeader key={`day-${d}`} label={d} />);
    }
    out.push(<div key={m.id}>{render(m)}</div>);
    if (i === last) out.push(<NewLine key="new-line" />);
  });
  return <>{out}</>;
}

