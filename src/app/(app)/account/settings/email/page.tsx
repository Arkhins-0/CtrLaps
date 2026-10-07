import { PageHeader } from "@/components/AppUI";
import { EmailSettings } from "@/components/EmailSettings";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Email" };

export default async function EmailPage() {
  await requireProfile();
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="Email" icon="mail" back="/account/settings" />
      <EmailSettings />
    </div>
  );
}
