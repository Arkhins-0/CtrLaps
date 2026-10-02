import { redirect } from "next/navigation";
import { AuthCard } from "@/components/AuthCard";
import { SignInForm } from "@/components/auth/SignInForm";
import { currentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function SignIn({ searchParams }: { searchParams: Promise<{ next?: string; deleted?: string }> }) {
  const user = await currentUser();
  if (user) redirect(user.profile_completed_at ? "/home" : "/onboarding");
  const { next, deleted } = await searchParams;
  return (
    <AuthCard title="Sign in">
      {deleted && (
        <p className="mb-4 rounded-lg border border-snow/15 bg-snow/5 p-3 text-sm text-snow-soft">
          Your account will be deleted in 7 days. Changed your mind? Sign in before then and it stays.
        </p>
      )}
      <SignInForm next={next} />
    </AuthCard>
  );
}
