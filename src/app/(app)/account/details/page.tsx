import { DetailRow, PageHeader } from "@/components/AppUI";
import { DetailsActions } from "@/components/account/DetailsActions";
import { editsOwnProfile, isDeveloper, roleLabel } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { toPublic, userById } from "@/lib/users";

export const metadata = { title: "Account" };

/** Your details as icon rows, then Edit details, Change password and Forgot password, each in a sheet. */
export default async function AccountDetails() {
  const user = await requireProfile();
  const p = toPublic(user);
  const parent = user.parent_id ? await userById(user.parent_id) : undefined;
  const canEdit = editsOwnProfile(user.role);
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="Account" icon="person" back="/account" />
      <DetailRow icon="person" label="Name" value={p.name ?? "—"} />
      <DetailRow icon="mail" label="Email" value={p.email} />
      <DetailRow icon="call" label="Contact" value={p.phone ?? "—"} />
      <DetailRow icon="calendar" label="Date of birth" value={p.dob ?? "—"} />
      <DetailRow icon="badge" label="Role" value={`${p.roleLabel}${p.teamName ? ` · ${p.teamName}` : ""}`} />
      {parent && <DetailRow icon="people" label="Reports to" value={`${parent.name || parent.email} · ${roleLabel(parent.role, isDeveloper(parent))}`} />}
      {!canEdit && <p className="px-2 pt-2 text-sm text-snow-faint">Profile details are locked. Your manager or an admin can change them.</p>}
      <DetailsActions
        email={p.email}
        profile={canEdit ? { name: p.name ?? "", dob: p.dob ?? "", phone: p.phone ?? "", email: p.email, photoUrl: p.photoUrl, admin: user.role === "admin" } : null}
      />
    </div>
  );
}
