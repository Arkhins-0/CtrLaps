import { PageHeader } from "@/components/AppUI";
import { NotificationSettings } from "@/components/account/NotificationSettings";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Notifications" };

/** Which kinds of popups this account's phone gets, as the app's Notifications settings. */
export default async function NotificationsPage() {
  await requireProfile();
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="Notifications" icon="bell" back="/account/settings" />
      <NotificationSettings />
    </div>
  );
}
