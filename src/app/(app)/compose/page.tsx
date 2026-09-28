import { ComposeForm } from "@/components/ComposeForm";
import { categoryRoster } from "@/lib/categoryChannels";
import { descendants } from "@/lib/hierarchy";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "New message" };

export default async function Compose() {
  const user = await requireProfile();
  const [below, categories] = await Promise.all([descendants(user), categoryRoster()]);
  const people = below.filter((p) => p.status === "active").map(toPublic);
  return (
    <div className="space-y-4">
      <h1 className="page-title">New message</h1>
      {people.length === 0 ? (
        <p className="card text-sm text-snow-faint">There is nobody below you to message yet.</p>
      ) : (
        <ComposeForm people={people} categories={categories} />
      )}
    </div>
  );
}
