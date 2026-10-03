"use client";

import Link from "next/link";
import type { CategoryChannel } from "@/lib/categoryChannels";
import type { ChannelSeason } from "@/lib/channels";
import { WhenLabel } from "../ChatList";
import { BellIcon } from "./BellIcon";

/**
 * The broadcast channels: one per race weekend, listed season by season
 * with the current season on top. A row opens its weekend. Above them, every race category's channel (everyone sees
 * them all). A muted channel's count is drawn inverted. Closing a channel and picking its managers happen inside the
 * channel, from its ⋮ menu (ChannelMenu).
 */
export function ChannelList({ initial, categories = [] }: { initial: ChannelSeason[]; categories?: CategoryChannel[] }) {
  const shown = initial.filter((s) => s.weekends.length > 0);

  return (
    <div className="-mx-4 min-h-0 flex-1 overflow-y-auto pb-4 sm:-mx-6 lg:mx-0">
      {categories.length > 0 && (
        <section className="mb-3">
          <div className="px-4 py-2 sm:px-6 lg:px-3">
            <p className="section-title">Categories</p>
          </div>
          {categories.map((c, i) => (
            <div key={c.id}>
              {i > 0 && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
              <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3">
                <Link href={`/c/${c.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                  <span
                    className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] text-[12px] font-bold tracking-wide"
                    style={{ color: c.color, backgroundColor: `${c.color}26` }}
                    aria-hidden
                  >
                    {c.code.slice(0, 5)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium">
                      {c.name}
                      {c.muted && <BellIcon muted className="ml-1.5 inline h-3.5 w-3.5 text-snow-faint" />}
                    </span>
                    <span className={`block truncate text-[13px] ${c.unread > 0 ? "text-snow" : "text-snow-faint"}`}>{c.lastMessage ?? "No posts yet"}</span>
                  </span>
                </Link>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  {c.lastMessageAt && (
                    <span className={`text-[11px] ${c.unread > 0 && !c.muted ? "text-gold" : "text-snow-faint"}`}>
                      <WhenLabel iso={c.lastMessageAt} />
                    </span>
                  )}
                  {c.unread > 0 && <span className={c.muted ? "badge-muted" : "badge"}>{c.unread > 99 ? "99+" : c.unread}</span>}
                </span>
              </div>
            </div>
          ))}
        </section>
      )}
      {shown.length === 0 && <p className="px-4 py-3 text-sm text-snow-faint sm:px-6 lg:px-3">No race weekends yet.</p>}
      {shown.map((s) => (
        <section key={s.id} className="mb-3">
          <div className="flex items-center gap-2 px-4 py-2 sm:px-6 lg:px-3">
            <p className="section-title flex-1">{s.name}</p>
            {s.current ? (
              <span className="chip border-gold/50 px-2 py-0 text-[10px] text-gold">Current</span>
            ) : s.status === "archived" ? (
              <span className="chip px-2 py-0 text-[10px] text-snow-faint">Archived</span>
            ) : null}
          </div>
          {s.weekends.map((w, i) => (
            <div key={w.id}>
              {i > 0 && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
              <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3">
                <Link href={`/w/${w.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                  <span
                    className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] text-xl ${w.channelOpen ? "bg-gold/15" : "border border-night-line bg-night-panel"}`}
                    aria-hidden
                  >
                    📣
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium">
                      {w.name}
                      {w.muted && <BellIcon muted className="ml-1.5 inline h-3.5 w-3.5 text-snow-faint" />}
                    </span>
                    <span className={`block truncate text-[13px] ${w.unread > 0 ? "text-snow" : "text-snow-faint"}`}>
                      {w.lastMessage ?? `${w.startsOn} → ${w.endsOn}${w.channelOpen ? "" : " · closed"}`}
                    </span>
                    {w.managers.length > 0 && (
                      <span className="block truncate text-[11px] text-snow-faint">Managed by {w.managers.map((m) => m.name).join(", ")}</span>
                    )}
                  </span>
                </Link>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  {w.lastMessageAt && (
                    <span className={`text-[11px] ${w.unread > 0 && !w.muted ? "text-gold" : "text-snow-faint"}`}>
                      <WhenLabel iso={w.lastMessageAt} />
                    </span>
                  )}
                  {w.unread > 0 && <span className={w.muted ? "badge-muted" : "badge"}>{w.unread > 99 ? "99+" : w.unread}</span>}
                </span>
              </div>
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}
