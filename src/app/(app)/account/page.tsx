import QRCode from "qrcode";
import { MenuLink, ProfileBanner } from "@/components/AppUI";
import { AccountTop, SignOutRow } from "@/components/account/AccountClient";
import { Avatar } from "@/components/Avatar";
import { FollowCategories } from "@/components/FollowCategories";
import { StatusBadge } from "@/components/StatusBadge";
import { latestRelease } from "@/lib/appReleases";
import { categoriesOf } from "@/lib/categories";
import { env } from "@/lib/env";
import { isDeveloper } from "@/lib/roles";
import { currentSeason } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";
import { followedCategories } from "@/lib/teams";
import { qrUrl, toPublic } from "@/lib/users";

export const metadata = { title: "Account" };

/**
 * The Account tab, as the app's: Search settings and the QR, a profile banner, then flat rows into Account, Archive,
 * Settings, Help & support and About, and Sign out.
 */
export default async function Account() {
  const user = await requireProfile();
  const p = toPublic(user);
  const season = await currentSeason();
  const dev = isDeveloper(user);
  const [svg, release, categories, following] = await Promise.all([
    QRCode.toString(qrUrl(user), { type: "svg", margin: 1, color: { dark: "#0B0B0C", light: "#FFFFFF" } }),
    latestRelease().catch(() => null),
    // Users (no role yet) follow categories as fans; everyone else has theirs by role.
    user.role === "user" ? categoriesOf([season.id]) : Promise.resolve([]),
    user.role === "user" ? followedCategories(user.id, season.id) : Promise.resolve([]),
  ]);

  return (
    <div className="flat mx-auto max-w-2xl space-y-4">
      <AccountTop qrSvg={svg} code={p.verifyCode} dev={dev}>
        <div className="space-y-4">
          <ProfileBanner
            photo={p.photoUrl}
            name={p.name ?? p.email}
            role={`${p.roleLabel}${p.teamName ? ` · ${p.teamName}` : ""}`}
            status={<StatusBadge status={p.status} />}
            photoSlot={<Avatar src={p.photoUrl} name={p.name ?? p.email} size={80} />}
          />
          {user.role === "user" && <FollowCategories categories={categories} initial={following} />}
          <nav>
            <MenuLink href="/account/details" icon="person" title="Account" hint="Email, date of birth and password" />
            <MenuLink href="/archive" icon="archive" title="Archive" hint="Past seasons: their weekends, channels and messages" />
            <MenuLink href="/account/settings" icon="settings" title="Settings" hint="Theme, email and your account" />
            {dev && <MenuLink href="/activity" icon="filter" title="Activity log" hint="Who did what, and when" />}
            <MenuLink href="/support" icon="call" title="Help & support" hint="FAQs, the support form and your tickets" />
            <MenuLink href="/account/about" icon="info" title="About" hint={release ? `The Android app is at v${release.version}` : "The app, the team, terms and privacy"} />
            <SignOutRow />
          </nav>
          <p className="py-2 text-center text-xs text-snow-faint">
            CTR[L]APS{release ? ` v${release.version}` : ""}
            {env.brand.mainDomain ? ` · ${env.brand.mainDomain}` : ""}
          </p>
        </div>
      </AccountTop>
    </div>
  );
}
