"use client";

import type { SaveProgress } from "@/lib/pendingEdits";

/**
 * The bar for changes waiting to be saved: "3 changes" with Discard and Save, then "Saving 2 of 3…" while they go.
 * Docked at the bottom, above the phone's tab bar and beside the laptop's sidebar. The gold line on top pulses while
 * changes wait and fills as they save.
 */
export function SaveBar({
  count,
  progress,
  onSave,
  onDiscard,
  label = "Save",
}: {
  count: number;
  progress: SaveProgress | null;
  onSave: () => void;
  onDiscard: () => void;
  label?: string;
}) {
  const saving = progress !== null;
  if (count === 0 && !saving) return null;
  const pct = progress && progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 100;
  return (
    <div className="fixed inset-x-0 bottom-[calc(4.25rem+env(safe-area-inset-bottom))] z-40 border-t border-night-line bg-night-panel/95 backdrop-blur lg:bottom-0 lg:left-64 lg:pb-[env(safe-area-inset-bottom)]">
      <div className="h-[3px] w-full bg-night-line">
        <div className={`h-full bg-gold transition-[width] duration-300 ${saving ? "" : "animate-pulse"}`} style={{ width: `${pct}%` }} />
      </div>
      <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2.5 sm:px-6">
        <p className="min-w-0 text-sm text-snow-soft" role="status">
          {saving ? (
            <>
              Saving <span className="font-semibold tabular-nums text-gold">{Math.min(progress.done + 1, progress.total)}</span> of{" "}
              <span className="tabular-nums">{progress.total}</span>…
            </>
          ) : (
            <>
              <span className="text-lg font-bold tabular-nums text-gold">{count}</span> {count === 1 ? "change" : "changes"}
              <span className="hidden sm:inline"> not saved yet</span>
            </>
          )}
        </p>
        <div className="ml-auto flex shrink-0 gap-2">
          <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={onDiscard} disabled={saving}>
            Discard
          </button>
          <button type="button" className="btn-gold px-5 py-1.5 text-xs" onClick={onSave} disabled={saving || count === 0}>
            {saving ? "Saving…" : label}
          </button>
        </div>
      </div>
    </div>
  );
}
