"use client";

import { useState } from "react";
import { Avatar } from "../Avatar";
import { Icon, type IconName } from "../Icon";
import { Sheet } from "../Sheet";

export type Credit = { id: string; name: string; subtext: string; link: string | null; photoUrl: string | null };

function SheetRow({ icon, title, hint, onClick }: { icon: IconName; title: string; hint: string; onClick: () => void }) {
  return (
    <button type="button" className="flex w-full items-center gap-5 rounded-xl px-2 py-3.5 text-left transition-colors hover:bg-snow/5" onClick={onClick}>
      <Icon name={icon} className="h-6 w-6 shrink-0 text-gold" />
      <span className="min-w-0 flex-1">
        <span className="block font-bold">{title}</span>
        <span className="block truncate text-sm text-snow-soft">{hint}</span>
      </span>
    </button>
  );
}

/** About's rows that open sheets: the people behind the app (from the database) and the License. */
export function AboutSheets({ credits, owner, licenseUrl }: { credits: Credit[]; owner: string; licenseUrl: string | null }) {
  const [sheet, setSheet] = useState<"people" | "license" | null>(null);
  return (
    <>
      <SheetRow icon="people" title="Developers / Helpers" hint="The people behind CTR[L]APS" onClick={() => setSheet("people")} />
      <SheetRow icon="info" title="License" hint={`Apache License 2.0${owner ? ` · © 2026 ${owner}` : ""}`} onClick={() => setSheet("license")} />
      {sheet === "people" && (
        <Sheet title="Developers / Helpers" onClose={() => setSheet(null)}>
          <div className="space-y-2.5 pb-2">
            {credits.length === 0 && <p className="text-sm text-snow-faint">Nobody listed yet.</p>}
            {credits.map((c) => {
              const body = (
                <>
                  <Avatar src={c.photoUrl} name={c.name} size={56} preview={false} />
                  <span className="min-w-0">
                    <span className="block truncate text-lg font-bold text-gold">{c.name}</span>
                    {c.subtext && <span className="block truncate text-sm">{c.subtext}</span>}
                  </span>
                </>
              );
              return c.link ? (
                <a key={c.id} href={c.link} target="_blank" rel="noreferrer" className="flex items-center gap-4 rounded-2xl bg-night-high p-3.5 hover:ring-1 hover:ring-gold/40">
                  {body}
                </a>
              ) : (
                <div key={c.id} className="flex items-center gap-4 rounded-2xl bg-night-high p-3.5">
                  {body}
                </div>
              );
            })}
          </div>
        </Sheet>
      )}
      {sheet === "license" && (
        <Sheet title="License" onClose={() => setSheet(null)}>
          <div className="space-y-3 pb-2 text-sm text-snow-soft">
            <p className="font-semibold text-snow">CTR[L]APS{owner ? ` · Copyright 2026 ${owner}` : ""}</p>
            <p>
              Licensed under the Apache License, Version 2.0. You may use, copy, change and share it under its terms; it comes with no warranty. The
              libraries it uses keep their own licenses, and the font, Plus Jakarta Sans, is under the SIL Open Font License.
            </p>
            {licenseUrl && (
              <a href={licenseUrl} target="_blank" rel="noreferrer" className="inline-block font-semibold text-gold hover:underline">
                Read the full license
              </a>
            )}
          </div>
        </Sheet>
      )}
    </>
  );
}
