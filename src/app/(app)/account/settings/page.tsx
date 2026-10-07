import { MenuLink, PageHeader } from "@/components/AppUI";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Settings" };

/** Settings, as the app's: Theme, Notifications, Email, Delete account. */
export default async function Settings() {
  await requireProfile();
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="Settings" icon="settings" back="/account" />
      <MenuLink href="/account/settings/theme" icon="palette" title="Theme" hint="Light or dark, pure black, the accent colour" />
      <MenuLink href="/account/settings/notifications" icon="bell" title="Notifications" hint="Which popups your phone gets" />
      <MenuLink href="/account/settings/email" icon="mail" title="Email" hint="Which emails you get" />
      <MenuLink href="/account/settings/delete" icon="trash" title="Delete account" hint="Erase your account and the details we hold" danger />
    </div>
  );
}
