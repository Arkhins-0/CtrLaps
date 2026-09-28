import { AuthCard } from "@/components/AuthCard";
import { SignupForm } from "@/components/auth/SignupForm";

export const metadata = { title: "Create your account" };

export default async function ConfirmRegistration({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <AuthCard title="Create your account">
      <SignupForm token={token} />
    </AuthCard>
  );
}
