import QRCode from "qrcode";
import { AccountActions } from "@/components/AccountActions";
import { CopyButton } from "@/components/CopyButton";
import { EmailSettings } from "@/components/EmailSettings";
import { FollowCategories } from "@/components/FollowCategories";
import { ProfileEditor } from "@/components/ProfileEditor";
import { Avatar } from "@/components/Avatar";
import { StatusBadge } from "@/components/StatusBadge";
import { latestRelease } from "@/lib/appReleases";
import { categoriesOf } from "@/lib/categories";
import { editsOwnProfile } from "@/lib/roles";
import { currentSeason } from "@/lib/seasons";
import { followedCategories } from "@/lib/teams";
import { isDeveloper, roleLabel } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { qrUrl, toPublic, userById } from "@/lib/users";

export const metadata = { title: "Account" };

export default async function Account() {
  const user = await requireProfile();
  const p = toPublic(user);
  const parent = user.parent_id ? await userById(user.parent_id) : undefined;
  const season = await currentSeason();
  const [svg, release, categories, following] = await Promise.all([
    QRCode.toString(qrUrl(user), { type: "svg", margin: 1, color: { dark: "#0B0B0C", light: "#FFFFFF" } }),
    latestRelease().catch(() => null),
    // Users (no role yet) follow categories as fans; everyone else has theirs by role.
    user.role === "user" ? categoriesOf([season.id]) : Promise.resolve([]),
    user.role === "user" ? followedCategories(user.id, season.id) : Promise.resolve([]),
  ]);

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_24rem]">
      <div className="space-y-5">
      <section className="card flex items-center gap-4">
        <Avatar src={p.photoUrl} name={p.name ?? p.email} size={72} />
        <div className="min-w-0">
          <h1 className="truncate text-lg font-semibold">{p.name}</h1>
          <p className="text-sm text-snow-soft">
            {p.roleLabel}
            {p.teamName ? ` · ${p.teamName}` : ""}
          </p>
          <div className="mt-1.5">
            <StatusBadge status={p.status} />
          </div>
        </div>
      </section>

      <section className="card grid gap-4 sm:grid-cols-[auto_1fr] sm:items-center">
        <div className="mx-auto w-40 rounded-xl bg-white p-2" dangerouslySetInnerHTML={{ __html: svg }} />
        <div className="space-y-3 text-sm">
          <div>
            <p className="label">Account code</p>
            <p className="flex items-center gap-1">
              <span className="font-mono text-2xl tracking-[0.2em]">{p.verifyCode}</span>
              <CopyButton value={p.verifyCode} label="Copy account code" />
            </p>
          </div>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <div>
              <p className="label">Email</p>
              <p className="truncate">{p.email}</p>
            </div>
            <div>
              <p className="label">Contact</p>
              <p>{p.phone}</p>
            </div>
            <div>
              <p className="label">Date of birth</p>
              <p>{p.dob}</p>
            </div>
            {parent && (
              <div>
                <p className="label">Reports to</p>
                <p className="truncate">
                  {parent.name || parent.email} <span className="text-snow-faint">· {roleLabel(parent.role, isDeveloper(parent))}</span>
                </p>
              </div>
            )}
          </div>
          {!editsOwnProfile(user.role) && <p className="text-xs text-snow-faint">Profile details are locked. Your manager or an admin can change them.</p>}
        </div>
      </section>

      {editsOwnProfile(user.role) && (
        <ProfileEditor profile={{ name: p.name ?? "", dob: p.dob ?? "", phone: p.phone ?? "", email: p.email, photoUrl: p.photoUrl, admin: user.role === "admin" }} />
      )}
      {user.role === "user" && <FollowCategories categories={categories} initial={following} />}
      <EmailSettings />
      </div>
      <AccountActions appVersion={release?.version ?? null} />
    </div>
  );
}
