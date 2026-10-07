"use client";

import { useCallback, useEffect, useState } from "react";

/**
 * Changes made on a page but not saved yet, one patch per item, holding only the fields that differ from what is
 * saved: changing a value and putting it back leaves no change behind. While any are waiting, leaving the page asks
 * first. Saving goes one item at a time, so the bar can count them; only the ones that fail stay for another try.
 */

export type Patch<T> = Partial<T>;

/** Whether two field values are the same; lists compare as sets (the order of picked chips doesn't matter). */
function same(a: unknown, b: unknown): boolean {
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x) => b.includes(x));
  return a === b;
}

/** `patch` without the fields that already match `saved`; null when nothing is left. */
export function normalizePatch<T extends object>(saved: T, patch: Patch<T>): Patch<T> | null {
  const out: Patch<T> = {};
  for (const key of Object.keys(patch) as (keyof T)[]) {
    if (!same(patch[key], saved[key])) out[key] = patch[key];
  }
  return Object.keys(out).length > 0 ? out : null;
}

export type SaveProgress = { done: number; total: number };

export function usePendingEdits<T extends { id: string }>() {
  const [patches, setPatches] = useState<Record<string, Patch<T>>>({});
  const [progress, setProgress] = useState<SaveProgress | null>(null);
  const count = Object.keys(patches).length;

  // Leaving with unsaved changes asks first.
  useEffect(() => {
    if (count === 0) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [count]);

  /** Change some fields of `saved`, on top of what is already waiting for it. */
  const change = useCallback((saved: T, fields: Patch<T>) => {
    setPatches((all) => {
      const next = normalizePatch(saved, { ...all[saved.id], ...fields });
      const rest = { ...all };
      delete rest[saved.id];
      return next ? { ...rest, [saved.id]: next } : rest;
    });
  }, []);

  /** The item as it shows: saved, with its waiting changes on top. */
  const view = useCallback((saved: T): T => ({ ...saved, ...patches[saved.id] }), [patches]);

  const discard = useCallback(() => setPatches({}), []);

  /**
   * Send every waiting patch, one after another, with [progress] counting them. The ones that went through are
   * dropped; the ones that failed stay. Returns how many of each.
   */
  const saveAll = useCallback(
    async (send: (id: string, patch: Patch<T>) => Promise<void>): Promise<{ saved: number; failed: number; error?: string }> => {
      const entries = Object.entries(patches);
      let saved = 0;
      let error: string | undefined;
      const failed: Record<string, Patch<T>> = {};
      setProgress({ done: 0, total: entries.length });
      for (const [id, patch] of entries) {
        try {
          await send(id, patch);
          saved++;
        } catch (e) {
          failed[id] = patch;
          error ??= e instanceof Error ? e.message : "Could not save.";
        }
        setProgress({ done: saved + Object.keys(failed).length, total: entries.length });
      }
      setPatches(failed);
      setProgress(null);
      return { saved, failed: Object.keys(failed).length, error };
    },
    [patches],
  );

  return { patches, count, change, view, discard, saveAll, progress, saving: progress !== null, isChanged: (id: string) => id in patches };
}
