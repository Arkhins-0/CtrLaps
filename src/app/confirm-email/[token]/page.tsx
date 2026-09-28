import { AuthCard } from "@/components/AuthCard";
import { ConfirmEmailForm } from "@/components/auth/ConfirmEmailForm";

export const metadata = { title: "Confirm your new email" };

export default async function ConfirmEmail({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <AuthCard title="Confirm your new email">
      <ConfirmEmailForm token={token} />
    </AuthCard>
  );
}
