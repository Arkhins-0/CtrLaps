import { notFound } from "next/navigation";
import { PageHeader } from "@/components/AppUI";
import { EmailForm } from "@/components/EmailForm";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Email" };

export default async function EmailPage({ searchParams }: { searchParams: Promise<{ group?: string }> }) {
  const user = await requireProfile();
  const { group } = await searchParams;
  if (user.role === "coordinator") {
    if (group !== "volunteers" && group !== "security") notFound();
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <PageHeader
          title={`Email my ${group}`}
          icon="mail"
          back="/people"
          sub={`Goes by email and as an urgent message to the ${group} ${group === "volunteers" ? "assigned to you" : "under you"}.`}
        />
        <EmailForm mode="relay" group={group} />
      </div>
    );
  }
  if (user.role === "admin") {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <PageHeader title="Email everyone" icon="mail" back="/people" sub="Tick the groups. Every active person in them gets the email and an urgent message." />
        <EmailForm mode="bulk" />
      </div>
    );
  }
  notFound();
}
