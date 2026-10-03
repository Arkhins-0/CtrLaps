import { notFound } from "next/navigation";
import { VolunteerGroupManager } from "@/components/volunteers/VolunteerGroupManager";
import { requireProfile } from "@/lib/session";
import { volunteerGroupDetail } from "@/lib/volunteers";

export const metadata = { title: "Volunteer group" };

/** Manage one volunteer group: its coordinator or an admin (anyone else: not found). */
export default async function VolunteerGroupPage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const group = await volunteerGroupDetail(user, id).catch(() => null);
  if (!group) notFound();
  return <VolunteerGroupManager initial={group} />;
}
