import { MenuLink, PageHeader } from "@/components/AppUI";
import { AboutSheets } from "@/components/account/AboutClient";
import { latestRelease } from "@/lib/appReleases";
import { q } from "@/lib/db";
import { env } from "@/lib/env";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "About" };

/** About, as the app's: the Android app, the people behind it (set in the database), Terms, Privacy and License. */
export default async function About() {
  await requireProfile();
  const [release, credits] = await Promise.all([
    latestRelease().catch(() => null),
    q<{ id: string; name: string; subtext: string; link: string; photo_url: string }>("SELECT id, name, subtext, link, photo_url FROM app_credits ORDER BY position, created_at").catch(() => []),
  ]);
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="About" icon="info" back="/account" />
      <MenuLink href="/download" plain icon="download" title="The Android app" hint={release ? `v${release.version}: download or update` : "Download it for your phone"} />
      <AboutSheets
        credits={credits.map((c) => ({ id: c.id, name: c.name, subtext: c.subtext, link: c.link || null, photoUrl: c.photo_url || null }))}
        owner={env.brand.poweredByName}
        licenseUrl={env.githubRepo ? `https://github.com/${env.githubRepo}/blob/main/LICENSE` : null}
      />
      <MenuLink href="/terms" icon="document" title="Terms and conditions" hint="The rules for using CTR[L]APS" />
      <MenuLink href="/privacy" icon="lock" title="Privacy Policy" hint="What CTR[L]APS keeps and why" />
    </div>
  );
}
