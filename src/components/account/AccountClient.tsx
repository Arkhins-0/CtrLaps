"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { MenuLink, SQUARE_BUTTON, SearchPill } from "../AppUI";
import { CopyButton } from "../CopyButton";
import { Icon, type IconName } from "../Icon";
import { Scanner } from "../Scanner";
import { Sheet } from "../Sheet";

/** Something Search settings finds: where it lives ("Settings › Theme"), the page, other words for it. */
type Findable = { title: string; path: string; href: string; words?: string; icon: IconName; dev?: boolean };

const FINDABLE: Findable[] = [
  { title: "Account details", path: "Account", href: "/account/details", words: "name profile date of birth phone contact photo", icon: "person" },
  { title: "Change email", path: "Account", href: "/account/details", words: "email address", icon: "mail" },
  { title: "Change password", path: "Account", href: "/account/details", words: "password security", icon: "lock" },
  { title: "Archive", path: "Archive", href: "/archive", words: "past seasons old", icon: "archive" },
  { title: "Theme", path: "Settings › Theme", href: "/account/settings/theme", words: "dark light mode system appearance", icon: "palette" },
  { title: "Pure black", path: "Settings › Theme", href: "/account/settings/theme", words: "amoled oled black dark", icon: "darkMode" },
  { title: "Accent colour", path: "Settings › Theme", href: "/account/settings/theme", words: "color gold orange green blue violet custom", icon: "palette" },
  { title: "Notifications", path: "Settings › Notifications", href: "/account/settings/notifications", words: "push popups alerts phone chats announcements channel posts results mute sound", icon: "bell" },
  { title: "Email", path: "Settings › Email", href: "/account/settings/email", words: "mail newsletters unsubscribe", icon: "mail" },
  { title: "Delete account", path: "Settings › Delete account", href: "/account/settings/delete", words: "remove erase close", icon: "trash" },
  { title: "Help & support", path: "Help & support", href: "/support", words: "help contact support ticket form", icon: "call" },
  { title: "FAQs", path: "Help & support", href: "/support", words: "questions help faq", icon: "help" },
  { title: "About", path: "About", href: "/account/about", words: "version app", icon: "info" },
  { title: "The Android app", path: "About", href: "/download", words: "android download install update apk", icon: "download" },
  { title: "Terms and conditions", path: "About", href: "/terms", words: "rules legal", icon: "document" },
  { title: "Privacy Policy", path: "About", href: "/privacy", words: "data privacy legal", icon: "lock" },
  { title: "Activity log", path: "Activity log", href: "/activity", words: "audit who did what", icon: "filter", dev: true },
];

function search(query: string, dev: boolean): Findable[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const words = q.split(/\s+/);
  return FINDABLE.filter((f) => dev || !f.dev)
    .map((f) => {
      const title = f.title.toLowerCase();
      const hay = `${title} ${f.path.toLowerCase()} ${f.words ?? ""}`;
      if (!words.every((w) => hay.includes(w))) return null;
      const score = (title === q ? 100 : title.startsWith(q) ? 75 : title.includes(q) ? 50 : 0) + words.length * 10;
      return { f, score };
    })
    .filter((x): x is { f: Findable; score: number } => x !== null)
    .sort((a, b) => b.score - a.score)
    .map((x) => x.f);
}

/**
 * The Account page's top, as the app's: Search settings with the QR button beside it. The QR opens in a sheet with
 * the account code and Verify someone; while searching, the results take the page's place.
 */
export function AccountTop({ qrSvg, code, dev, children }: { qrSvg: string; code: string; dev: boolean; children: React.ReactNode }) {
  const [query, setQuery] = useState("");
  const [sheet, setSheet] = useState<"qr" | "verify" | null>(null);
  const found = search(query, dev);
  return (
    <>
      <div className="flex items-center gap-2.5">
        <SearchPill query={query} onChange={setQuery} placeholder="Search settings" />
        <button type="button" className={SQUARE_BUTTON} aria-label="My QR code" onClick={() => setSheet("qr")}>
          <Icon name="scan" className="h-6 w-6" />
        </button>
      </div>
      {query.trim() ? (
        found.length === 0 ? (
          <div className="py-10 text-center">
            <p className="font-semibold">No settings match</p>
            <p className="text-sm text-snow-faint">Try another word, like theme, password or email.</p>
          </div>
        ) : (
          <div>
            {found.map((f) => (
              <MenuLink key={f.title} href={f.href} icon={f.icon} title={f.title} hint={f.path} />
            ))}
          </div>
        )
      ) : (
        children
      )}
      {sheet === "qr" && (
        <Sheet title="My QR code" onClose={() => setSheet(null)}>
          <div className="flex flex-col items-center gap-4 pb-2">
            <div className="w-60 rounded-2xl bg-white p-3" dangerouslySetInnerHTML={{ __html: qrSvg }} />
            <div className="text-center">
              <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-snow-faint">Account code</p>
              <p className="flex items-center justify-center gap-1">
                <span className="font-mono text-2xl tracking-[0.2em]">{code}</span>
                <CopyButton value={code} label="Copy account code" />
              </p>
            </div>
            <button type="button" className="btn-ghost w-full py-3" onClick={() => setSheet("verify")}>
              <Icon name="search" className="h-5 w-5 text-gold" />
              Verify someone
            </button>
          </div>
        </Sheet>
      )}
      {sheet === "verify" && (
        <Sheet title="Verify someone" onClose={() => setSheet(null)}>
          <Scanner />
        </Sheet>
      )}
    </>
  );
}

/** Sign out, as a red row; it asks first. */
export function SignOutRow() {
  const router = useRouter();
  const [asking, setAsking] = useState(false);
  const signOut = async () => {
    await api("/api/auth/logout", { method: "POST", json: {} }).catch(() => null);
    router.replace("/login");
    router.refresh();
  };
  return (
    <>
      <button type="button" className="flex w-full items-center gap-5 rounded-xl px-2 py-3.5 text-left transition-colors hover:bg-snow/5" onClick={() => setAsking(true)}>
        <Icon name="logout" className="h-6 w-6 shrink-0 text-danger" />
        <span className="min-w-0 flex-1">
          <span className="block font-bold text-danger">Sign out</span>
          <span className="block truncate text-sm text-snow-soft">You can sign in again any time</span>
        </span>
      </button>
      {asking && (
        <Sheet title="Sign out?" onClose={() => setAsking(false)}>
          <div className="space-y-4 pb-2">
            <p className="text-center text-sm text-snow-soft">You can sign in again any time with your email and password.</p>
            <button type="button" className="btn-danger w-full py-3" onClick={signOut}>
              Sign out
            </button>
          </div>
        </Sheet>
      )}
    </>
  );
}
