import Link from "next/link";
import { PeopleList } from "@/components/PeopleList";
import { categoryRoster } from "@/lib/categoryChannels";
import { descendants } from "@/lib/hierarchy";
import { CREATE_RULES } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "People" };

export default async function People() {
  const user = await requireProfile();
  const [below, categories] = await Promise.all([descendants(user), categoryRoster()]);
  const people = below.map(toPublic);
  const canCreate = (CREATE_RULES[user.role] ?? []).length > 0;
  // An "Email" link on the Volunteers and Security titles: an admin picks the groups on the email page, a coordinator
  // writes to their own volunteers or security.
  const emailLinks =
    user.role === "admin"
      ? { volunteer: "/people/email", security: "/people/email" }
      : user.role === "coordinator"
        ? { volunteer: "/people/email?group=volunteers", security: "/people/email?group=security" }
        : undefined;

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
          {(user.role === "admin" || user.role === "coordinator") && (
            <Link href="/people/teams" className="btn-ghost px-3 py-1.5 text-xs">
              Teams
            </Link>
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

      <PeopleList
        people={people}
        categories={categories}
        emailLinks={emailLinks}
        emptyText={canCreate ? "The people you add, and everyone who reports to you, show here." : "Nobody reports to you yet. Those who do will show here."}
        emptyAction={canCreate ? { label: "Add someone", href: "/people/new" } : undefined}
      />
    </div>
  );
}
