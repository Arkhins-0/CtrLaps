import { notFound } from "next/navigation";
import { ComposeForm } from "@/components/ComposeForm";
import { categoryRoster } from "@/lib/categoryChannels";
import { announceCandidates } from "@/lib/hierarchy";
import { canAnnounce } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "New message" };

/** A new announcement: admins and coordinators, to anyone of any role (fans included). */
export default async function Compose() {
  const user = await requireProfile();
  if (!canAnnounce(user.role)) notFound();
  const [everyone, categories] = await Promise.all([announceCandidates(user), categoryRoster()]);
  const people = everyone.map(toPublic);
  return (
    <div className="space-y-4">
      <h1 className="page-title">New message</h1>
      {people.length === 0 ? <p className="card text-sm text-snow-faint">There is nobody to message yet.</p> : <ComposeForm people={people} categories={categories} />}
    </div>
  );
}
