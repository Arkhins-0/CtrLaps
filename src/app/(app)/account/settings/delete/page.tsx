import { PageHeader } from "@/components/AppUI";
import { DeleteMyAccount } from "@/components/DeleteAccount";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Delete account" };

export default async function DeletePage() {
  await requireProfile();
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="Delete account" icon="trash" back="/account/settings" />
      <DeleteMyAccount />
    </div>
  );
}
