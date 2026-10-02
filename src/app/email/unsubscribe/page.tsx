import { AuthCard } from "@/components/AuthCard";
import { UnsubscribeButton } from "@/components/UnsubscribeButton";
import { EMAIL_KIND_LABEL, isEmailKind } from "@/lib/emailPrefs";

export const metadata = { title: "Email" };
export const dynamic = "force-dynamic";

/** Where a mail's "Stop these emails" link lands: one button stops that kind (no sign-in), with a way to all choices. */
export default async function Unsubscribe({ searchParams }: { searchParams: Promise<{ u?: string; k?: string }> }) {
  const { u = "", k = "" } = await searchParams;
  if (!isEmailKind(k) || !/^[0-9a-f-]{36}$/i.test(u)) {
    return (
      <AuthCard title="Email">
        <p className="text-sm text-snow-soft">This link isn&apos;t complete. Choose which emails you get on your Account page.</p>
        <a href="/account#email" className="btn-gold mt-4 w-full">
          Manage email
        </a>
      </AuthCard>
    );
  }
  const kind = EMAIL_KIND_LABEL[k];
  return (
    <AuthCard title="Stop these emails?">
      <UnsubscribeButton u={u} k={k} label={kind.label} />
    </AuthCard>
  );
}
