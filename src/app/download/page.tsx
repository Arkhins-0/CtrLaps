import Image from "next/image";
import Link from "next/link";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import QRCode from "qrcode";
import { Icon } from "@/components/Icon";
import { DownloadButton } from "@/components/DownloadButton";
import { latestRelease } from "@/lib/appReleases";
import { currentUser } from "@/lib/auth";
import { APP_NAME, SITE_URL } from "@/lib/config";

export const metadata = { title: "Get the app" };
export const dynamic = "force-dynamic";

const STEPS: { title: string; line: string }[] = [
  { title: "Download", line: "Tap Download. If your browser asks whether to keep the file, keep it." },
  { title: "Open it", line: "Open the file from the notification or your Downloads. When Android asks, allow your browser to install apps." },
  { title: "Install", line: `Tap Install (or Update), then open ${APP_NAME} and sign in.` },
];

/**
 * <site>/download, the one link to share: the Android app's latest version and what's new in it, a Download button,
 * a QR code to open this page on a phone, and how to install. Open to everyone, signed in or not.
 *
 * The download itself is /download/apk (what this address did before). Old links with `?open=<path>` (open a page in
 * the installed app: AppChooser, openInAppHref) or `?direct=1` (the installed app's fallback) still go there.
 */
export default async function DownloadPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const params = await searchParams;
  const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);
  const open = first(params.open);
  const direct = first(params.direct);
  if (open !== undefined || direct === "1") {
    const forward = new URLSearchParams();
    if (open !== undefined) forward.set("open", open);
    if (direct === "1") forward.set("direct", "1");
    redirect(`/download/apk?${forward}`);
  }

  const [release, user, qr, agent] = await Promise.all([
    latestRelease().catch(() => null),
    currentUser().catch(() => null),
    QRCode.toString(`${SITE_URL}/download`, { type: "svg", margin: 1, color: { dark: "#0B0B0C", light: "#FFFFFF" } }),
    headers().then((h) => h.get("user-agent") ?? ""),
  ]);
  const android = /android/i.test(agent);
  // Signed out, the page shows only the notes for everyone; lines tagged for a role are for those inside the app.
  const notes = (release?.sections ?? []).map((s) => ({ title: s.title, items: s.items.filter((i) => i.roles.length === 0) })).filter((s) => s.items.length > 0);

  return (
    <main className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6 sm:py-12">
      <div className="mb-10 flex items-center gap-3">
        <Link href="/" className="flex items-center gap-3">
          <Image src="/logo.png" alt="" width={40} height={39} priority />
          <span className="text-xl font-bold tracking-tight">{APP_NAME}</span>
        </Link>
        <Link href={user ? "/home" : "/login"} className="btn-ghost ml-auto px-4 py-1.5 text-xs">
          {user ? "Open on the web" : "Sign in"}
        </Link>
      </div>

      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_17rem]">
        <section>
          <div className="flex items-center gap-4">
            <Image src="/icon-512.png" alt="" width={72} height={72} className="rounded-2xl" priority />
            <div className="min-w-0">
              <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{APP_NAME} for Android</h1>
              <p className="mt-1 text-snow-soft">{release ? `Version ${release.version}` : "The latest version, for Android phones"}</p>
            </div>
          </div>

          {/* Downloads, and shows what comes next (installing, Play Protect's warning) in a sheet. */}
          <DownloadButton href="/download/apk?direct=1" version={release?.version ?? null} />
          {android && (
            <p className="mt-3 text-sm text-snow-soft">
              Already have it?{" "}
              <a href="/download/apk" className="font-semibold text-gold hover:underline">
                Open the app
              </a>
            </p>
          )}

          <h2 className="mb-1 mt-10 text-xl font-bold tracking-tight">How to install</h2>
          <ol>
            {STEPS.map((s, i) => (
              <li key={s.title} className="flex gap-4 px-1 py-3">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-gold/15 text-sm font-bold text-gold">{i + 1}</span>
                <span className="min-w-0">
                  <span className="block font-bold">{s.title}</span>
                  <span className="block text-sm text-snow-soft">{s.line}</span>
                </span>
              </li>
            ))}
          </ol>

          <div className="mt-4 flex gap-4 rounded-2xl border border-night-line bg-night-panel px-4 py-3.5">
            <Icon name="info" className="mt-0.5 h-5 w-5 shrink-0 text-gold" />
            <p className="text-sm text-snow-soft">
              <span className="font-bold text-snow">Xiaomi, Redmi or POCO phone?</span> Allow installs from your browser when asked (or search Settings
              for &ldquo;Install unknown apps&rdquo;). MIUI may scan the app and ask you to confirm before it installs: tap to continue.
            </p>
          </div>

          {notes.length > 0 && (
            <>
              <h2 className="mb-1 mt-10 text-xl font-bold tracking-tight">What&apos;s new{release ? ` in v${release.version}` : ""}</h2>
              {notes.map((s, i) => (
                <div key={`${s.title}-${i}`} className="mt-3">
                  {s.title && <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-snow-faint">{s.title}</p>}
                  <ul className="mt-1 list-disc space-y-1.5 pl-5 text-sm text-snow-soft marker:text-gold">
                    {s.items.map((item, j) => (
                      <li key={j}>{item.text}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </>
          )}
        </section>

        {/* On a laptop: scan to carry on on the phone. On the phone itself it's last, for showing to someone else. */}
        <aside className="flex flex-col items-center gap-3 text-center lg:pt-2">
          <div className="w-56 rounded-2xl bg-white p-3" role="img" aria-label="QR code for this page" dangerouslySetInnerHTML={{ __html: qr }} />
          <p className="max-w-[16rem] text-sm text-snow-soft">Scan with your phone&apos;s camera to open this page there.</p>
        </aside>
      </div>
    </main>
  );
}
