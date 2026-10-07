import { PageHeader } from "@/components/AppUI";
import { ThemeSettings } from "@/components/account/ThemeSettings";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Theme" };

export default async function ThemePage() {
  await requireProfile();
  return (
    <div className="flat mx-auto max-w-2xl">
      <PageHeader title="Theme" icon="palette" back="/account/settings" />
      <ThemeSettings />
    </div>
  );
}
