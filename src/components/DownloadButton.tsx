"use client";

import { useState } from "react";
import { APP_NAME } from "@/lib/config";
import { Icon } from "./Icon";
import { PlayProtectGuide } from "./PlayProtectGuide";
import { Sheet } from "./Sheet";

/**
 * The download page's Download button. The tap starts the download as a plain link would, and at the same moment a
 * sheet shows what comes next: open the file, install, and how to get past Play Protect's warning (which only makes
 * sense once someone is installing, so it isn't on the page itself).
 */
export function DownloadButton({ href, version }: { href: string; version: string | null }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      {/* A plain link, not prefetched: it leaves for the APK; the sheet opens over the page that stays. */}
      <a href={href} onClick={() => setOpen(true)} className="btn-gold mt-8 w-full gap-2 py-4 text-base sm:w-auto sm:px-10">
        <Icon name="download" className="h-6 w-6" />
        Download{version ? ` v${version}` : ""}
      </a>
      {open && (
        <Sheet title="Your download has started" onClose={() => setOpen(false)} wide>
          <div className="pb-2">
            <p className="text-sm text-snow-soft">
              When it finishes, open the file from the notification or your Downloads and tap <b className="text-snow">Install</b>. If Android asks,
              allow your browser to install apps. Then open {APP_NAME} and sign in.
            </p>
            <PlayProtectGuide />
            <p className="mt-6 text-sm text-snow-soft">
              Nothing downloading?{" "}
              <a href={href} className="font-semibold text-gold hover:underline">
                Try again
              </a>
            </p>
          </div>
        </Sheet>
      )}
    </>
  );
}
