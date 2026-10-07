"use client";

import { useEffect, useState } from "react";

export type CountdownSession = { name: string; startsAt: string; endsAt: string };

/** "1d 4h 12m", "4h 12m 30s", "12m 30s": the seconds only once it's less than a day away. */
function span(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(total / 86_400);
  const h = Math.floor((total % 86_400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  return `${m}m ${s}s`;
}

/**
 * A ticking line to a weekend's next session ("Qualifying in 1d 4h 12m"), "Live now" while one runs, and nothing once
 * they're all over. Ticks every second as the header's CountdownChip does, from the sessions it's given, so it needs
 * no server; drawn only after mount, as the server's clock and the reader's differ.
 */
export function SessionCountdown({ sessions, className = "" }: { sessions: CountdownSession[]; className?: string }) {
  const [now, setNow] = useState<number | null>(null);
  const lastEnd = Math.max(0, ...sessions.map((s) => new Date(s.endsAt).getTime()));

  useEffect(() => {
    setNow(Date.now());
    // Once the last session is over there's nothing left to count: the clock stops then, or when the page goes.
    if (Date.now() >= lastEnd) return;
    const tick = setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= lastEnd) clearInterval(tick);
    }, 1000);
    return () => clearInterval(tick);
  }, [lastEnd]);

  if (now === null) return null;
  const at = (iso: string) => new Date(iso).getTime();
  const live = sessions.find((s) => at(s.startsAt) <= now && now < at(s.endsAt));
  if (live) {
    return (
      <p className={`flex items-center gap-2 ${className}`}>
        <span className="h-2 w-2 shrink-0 rounded-full bg-gold motion-safe:animate-pulse" aria-hidden />
        <span>
          Live now<span className="font-normal text-snow-soft"> · {live.name}</span>
        </span>
      </p>
    );
  }
  const next = sessions.filter((s) => at(s.startsAt) > now).sort((a, b) => at(a.startsAt) - at(b.startsAt))[0];
  if (!next) return null;
  return (
    <p className={className}>
      {next.name} in <span className="tabular-nums">{span(at(next.startsAt) - now)}</span>
    </p>
  );
}
