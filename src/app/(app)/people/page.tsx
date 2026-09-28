import Link from "next/link";
import { PeopleList } from "@/components/PeopleList";
import { descendants } from "@/lib/hierarchy";
import { CREATE_RULES } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "People" };

export default async function People() {
  const user = await requireProfile();
  const people = (await descendants(user)).map(toPublic);
  const canCreate = (CREATE_RULES[user.role] ?? []).length > 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="page-title">People</h1>
        <div className="flex gap-2">
          {user.role === "coordinator" && (
            <>
              <Link href="/people/email?group=volunteers" className="btn-ghost px-3 py-1.5 text-xs">
                Email volunteers
              </Link>
              <Link href="/people/email?group=security" className="btn-ghost px-3 py-1.5 text-xs">
                Email security
              </Link>
            </>
          )}
          {user.role === "admin" && (
            <Link href="/people/email" className="btn-ghost px-3 py-1.5 text-xs">
              Email everyone
            </Link>
          )}
          {canCreate && (
            <Link href="/people/new" className="btn-gold px-4 py-1.5 text-xs">
              Add or promote
            </Link>
          )}
        </div>
      </div>

      <PeopleList people={people} emptyText={canCreate ? "Nobody yet. Add the first person." : "Nobody reports to you."} />
    </div>
  );
}
