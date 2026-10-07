import { PageHeader } from "@/components/AppUI";
import { VerifyCard } from "@/components/VerifyCard";
import { userColumns, type SessionUser } from "@/lib/auth";
import { one } from "@/lib/db";
import { isDeveloper, roleLabel } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { userPhotoUrl } from "@/lib/profile";

export const metadata = { title: "Verify" };

/** A scanned QR opened in the browser: the same card the in-app scanner shows. */
export default async function Verify({ params }: { params: Promise<{ token: string }> }) {
  await requireProfile();
  const { token } = await params;
  const user = await one<SessionUser>(`SELECT ${userColumns()} FROM users WHERE qr_token = $1`, [token.slice(0, 100)]);

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <PageHeader title="Verification" icon="scan" />
      {user ? (
        <VerifyCard
          v={{
            id: user.id,
            name: user.name,
            roleLabel: roleLabel(user.role, isDeveloper(user)),
            teamName: user.team_name,
            status: user.status,
            verifyCode: user.verify_code,
            photoUrl: userPhotoUrl(user.id, user.photo_key),
            profileComplete: Boolean(user.profile_completed_at),
          }}
        />
      ) : (
        <p className="error">No account matches this code.</p>
      )}
    </div>
  );
}
