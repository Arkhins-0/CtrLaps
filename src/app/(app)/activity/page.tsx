import { notFound } from "next/navigation";
import { ActivityLog } from "@/components/ActivityLog";
import { activity } from "@/lib/activity";
import { isDeveloper } from "@/lib/roles";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Activity log" };

/** The Activity log: what organisers did, who and when. Developers only (anyone else gets "not found"). */
export default async function ActivityPage() {
  const user = await requireProfile();
  if (!isDeveloper(user)) notFound();
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-lg font-semibold">Activity log</h1>
        <p className="text-sm text-snow-faint">Who did what, and when. Only the support team sees this. Private chats and groups are never logged.</p>
      </div>
      <ActivityLog initial={await activity({})} />
    </div>
  );
}
