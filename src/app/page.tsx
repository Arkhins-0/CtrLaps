import Image from "next/image";
import Link from "next/link";
import { redirect } from "next/navigation";
import { latestRelease } from "@/lib/appReleases";
import { currentUser } from "@/lib/auth";
import { APP_NAME } from "@/lib/config";
import { env } from "@/lib/env";

export const dynamic = "force-dynamic";

const FEATURES: { title: string; body: string; tone: string }[] = [
  {
    title: "Announcements that reach everyone",
    body: "Organisers write once, to everyone or to chosen roles and categories. Urgent ones go by email too.",
    tone: "bg-gold",
  },
  {
    title: "Race-weekend and category channels",
    body: "One channel per weekend and one per category, so updates land where the people they're for are looking.",
    tone: "bg-gold-deep",
  },
  {
    title: "Your own schedule",
    body: "Your sessions first, a countdown to the next one, and times in the track's time zone.",
    tone: "bg-snow",
  },
  {
    title: "Results and standings",
    body: "Results per session and standings round by round, for every category, the moment they're published.",
    tone: "bg-gold",
  },
  {
    title: "Chats and groups",
    body: "Private chats, groups, delegations and volunteer groups, with photos, documents, voice notes and polls.",
    tone: "bg-gold-deep",
  },
  {
    title: "Verify anyone by QR",
    body: "Every account has an ID card. Scan its code at the gate to see who someone is and what they're there for.",
    tone: "bg-snow",
  },
];

/**
 * The front door: what CTR[L]APS is, where to get it, and the way in. Signed-in people go straight to Home; signing
 * in is at /login.
 */
export default async function Landing() {
  const user = await currentUser();
  if (user) redirect(user.profile_completed_at ? "/home" : "/onboarding");
  // The live version, if GitHub answers (cached on the server); the page stands without it.
  const release = await latestRelease().catch(() => null);
  const year = new Date().getFullYear();

  return (
    <div className="min-h-screen overflow-x-hidden">
      <header className="sticky top-0 z-40 border-b border-night-line/60 bg-night/75 backdrop-blur-xl">
        <div className="container-x flex h-16 items-center gap-3">
          <Link href="/" className="flex items-center gap-2.5">
            <Image src="/logo.png" alt="" width={36} height={36} priority />
            <Wordmark className="text-lg" />
          </Link>
          <nav className="ml-auto flex items-center gap-2">
            <a href="#features" className="hidden px-3 py-1.5 text-sm text-snow-soft hover:text-snow sm:block">
              Features
            </a>
            <Link href="/login" className="btn-ghost px-4 py-1.5 text-sm">
              Sign in
            </Link>
            <a href="/download" className="btn-gold px-4 py-1.5 text-sm shadow-[0_6px_20px_-6px_rgb(255_209_0/0.5)]">
              Get the app
            </a>
          </nav>
        </div>
      </header>

      <main>
        <section className="relative">
          <div className="hero-glow pointer-events-none absolute inset-0" />
          <div className="grid-fade pointer-events-none absolute inset-0" />
          <div className="container-x relative grid items-center gap-14 py-16 md:grid-cols-[1.1fr_0.9fr] md:py-24">
            <div>
              <a
                href="/download"
                className="inline-flex items-center gap-2 rounded-full border border-night-line bg-night-panel/70 px-3 py-1 text-xs text-snow-soft backdrop-blur hover:text-snow"
              >
                <span className="h-1.5 w-1.5 rounded-full bg-gold" />
                {release ? `Version ${release.version} is out` : "For Android and the web"}
              </a>
              <h1 className="mt-6 text-4xl font-bold leading-[1.05] tracking-tight sm:text-6xl">
                Race weekend,
                <br />
                <span className="bg-gradient-to-r from-gold via-gold-deep to-snow bg-clip-text text-transparent">one channel.</span>
              </h1>
              <p className="mt-5 max-w-xl text-lg text-snow-soft">
                Announcements, documents, schedule changes and chats, straight from the people running the weekend to the people on the
                ground.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <a
                  href="/download"
                  className="btn-gold gap-2 px-6 py-3 text-base shadow-[0_10px_30px_-8px_rgb(255_209_0/0.5)] transition-transform hover:-translate-y-0.5"
                >
                  <DownloadIcon />
                  Download for Android
                </a>
                <Link href="/login" className="btn-ghost bg-night-panel/60 px-6 py-3 text-base backdrop-blur">
                  Open on the web
                </Link>
              </div>
              <p className="mt-5 text-xs text-snow-faint">Android 8 or newer · Updates itself · Sign in with your email</p>
            </div>
            <PhoneMockup />
          </div>
        </section>

        <section id="features" className="container-x scroll-mt-20 py-16 md:py-24">
          <p className="text-sm font-semibold uppercase tracking-wider text-gold">Why {APP_NAME}</p>
          <h2 className="mt-2 max-w-2xl text-3xl font-bold tracking-tight sm:text-4xl">Everything the paddock needs to know, in one place.</h2>
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <div
                key={f.title}
                className="rounded-3xl border border-night-line bg-night-panel p-6 transition hover:-translate-y-1 hover:border-snow/15"
              >
                <span className={`block h-1.5 w-10 rounded-full ${f.tone}`} />
                <h3 className="mt-4 text-lg font-semibold">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-snow-soft">{f.body}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="border-y border-night-line bg-night-panel/40">
          <div className="container-x grid gap-4 py-16 md:grid-cols-2 md:py-20">
            <WayIn
              eyebrow="Android app"
              title="On your phone"
              body="Notifications with Reply and Mark read, the schedule and channels kept on the phone for when the signal drops, and updates that install from inside the app."
              cta={{ href: "/download", label: "Download the app" }}
            />
            <WayIn
              eyebrow="Website"
              title="On any computer"
              body="The same account in a browser: chats, announcements, the schedule and results, with a bigger screen for setting up a weekend."
              cta={{ href: "/login", label: "Sign in on the web" }}
            />
          </div>
        </section>

        <section className="container-x flex flex-col items-start gap-6 py-16 sm:flex-row sm:items-center sm:justify-between md:py-20">
          <div>
            <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">Ready for the next round.</h2>
            <p className="mt-2 text-snow-soft">
              New here?{" "}
              <Link href="/register" className="text-snow underline decoration-gold underline-offset-4">
                Create an account
              </Link>{" "}
              and your team adds you to its weekend.
            </p>
          </div>
          <a href="/download" className="btn-gold gap-2 px-6 py-3 text-base">
            <DownloadIcon />
            Download for Android
          </a>
        </section>
      </main>

      <footer className="border-t border-night-line">
        <div className="container-x grid gap-4 py-8 text-sm text-snow-faint sm:grid-cols-[1fr_auto] sm:items-center">
          <div className="flex items-center gap-2.5">
            <Image src="/logo.png" alt="" width={28} height={28} />
            <Wordmark className="text-sm text-snow" />
            <span>
              · © {year}
              {env.brand.poweredByName ? (
                <>
                  {" "}
                  · Powered by{" "}
                  {env.brand.poweredByDomain ? (
                    <a href={`https://${env.brand.poweredByDomain}`} className="hover:text-snow">
                      {env.brand.poweredByName}
                    </a>
                  ) : (
                    env.brand.poweredByName
                  )}
                </>
              ) : null}
            </span>
          </div>
          <nav className="flex flex-wrap gap-4">
            <a href="/download" className="hover:text-snow">
              Download
            </a>
            <Link href="/help" className="hover:text-snow">
              Help
            </Link>
            <Link href="/privacy" className="hover:text-snow">
              Privacy
            </Link>
            <Link href="/terms" className="hover:text-snow">
              Terms
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}

/** CTR[L]APS with the [L] in gold, as in the app's header. */
function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span className={`font-bold tracking-tight ${className}`}>
      CTR<span className="text-gold">[L]</span>APS
    </span>
  );
}

function WayIn({ eyebrow, title, body, cta }: { eyebrow: string; title: string; body: string; cta: { href: string; label: string } }) {
  return (
    <div className="flex flex-col rounded-3xl border border-night-line bg-night p-7">
      <p className="text-xs font-semibold uppercase tracking-wider text-gold">{eyebrow}</p>
      <h3 className="mt-2 text-2xl font-bold tracking-tight">{title}</h3>
      <p className="mt-3 flex-1 text-sm leading-relaxed text-snow-soft">{body}</p>
      <a href={cta.href} className="btn-ghost mt-6 self-start px-5 py-2 text-sm">
        {cta.label}
      </a>
    </div>
  );
}

function DownloadIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M12 3v12" />
      <path d="m7 10 5 5 5-5" />
      <path d="M5 21h14" />
    </svg>
  );
}

/**
 * The app drawn in markup, no screenshots to keep up to date: Home with the next session, a standings card and a
 * chat, and a notification floating over it with its Reply and Mark read buttons.
 */
function PhoneMockup() {
  return (
    <div className="relative mx-auto w-full max-w-[300px] md:mx-0 md:justify-self-end">
      <div className="float">
        <div className="rounded-[2.6rem] border border-night-line bg-night-panel p-2.5 shadow-[0_40px_80px_-30px_rgb(255_209_0/0.35)]">
          <div className="overflow-hidden rounded-[2.1rem] bg-night">
            <div className="flex items-center justify-between px-5 pb-1 pt-3 text-[10px] text-snow-soft">
              <span>9:41</span>
              <span className="h-4 w-16 rounded-full bg-night-panel" />
              <span>100%</span>
            </div>
            <div className="flex items-center justify-between px-4 py-2">
              <Wordmark className="text-sm" />
              <span className="rounded-full border border-gold/60 px-2 py-0.5 text-[9px] font-semibold text-gold">QUALI 1d 04:12</span>
            </div>
            <div className="space-y-2.5 px-3 pb-5">
              <div className="rounded-2xl border border-night-line bg-night-panel p-3">
                <p className="text-[9px] font-semibold uppercase tracking-wider text-gold">Next session</p>
                <p className="mt-1 text-sm font-semibold">Round 3 · Qualifying</p>
                <p className="text-[11px] text-snow-faint">Sat 10:30 am · ITC</p>
              </div>
              <div className="rounded-2xl border border-night-line bg-night-panel p-3">
                <p className="text-[9px] font-semibold uppercase tracking-wider text-gold">Standings · ITC</p>
                {[
                  ["1", "A. Raman", "86"],
                  ["2", "K. Joseph", "74"],
                  ["3", "S. Iyer", "61"],
                ].map(([pos, name, pts]) => (
                  <div key={pos} className="mt-1.5 flex items-center gap-2 text-[11px]">
                    <span className="w-3 text-snow-faint">{pos}</span>
                    <span className="flex-1">{name}</span>
                    <span className="font-semibold text-gold">{pts}</span>
                  </div>
                ))}
              </div>
              <div className="rounded-2xl border border-night-line bg-night-panel p-3">
                <p className="text-[9px] font-semibold uppercase tracking-wider text-gold">Chats</p>
                <div className="mt-2 max-w-[85%] rounded-2xl rounded-bl-md bg-night-line px-3 py-1.5 text-[11px]">Tyres are in the pit garage.</div>
                <div className="ml-auto mt-1.5 max-w-[70%] rounded-2xl rounded-br-md bg-gold px-3 py-1.5 text-[11px] text-night">On my way.</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="absolute -bottom-8 -left-4 w-60 sm:-bottom-6 rounded-2xl border border-night-line bg-night-panel/95 p-3 shadow-2xl backdrop-blur sm:-left-14">
        <div className="flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-gold text-[10px] font-bold text-night">RC</span>
          <p className="text-[11px] font-semibold">Race control · Delegation</p>
          <span className="ml-auto text-[9px] text-snow-faint">now</span>
        </div>
        <p className="mt-1.5 text-[11px] text-snow-soft">Pit lane opens 10 minutes late.</p>
        <div className="mt-2 flex gap-1.5">
          <span className="rounded-full border border-night-line px-2.5 py-0.5 text-[10px] text-snow">Reply</span>
          <span className="rounded-full border border-night-line px-2.5 py-0.5 text-[10px] text-snow">Mark read</span>
        </div>
      </div>
    </div>
  );
}
