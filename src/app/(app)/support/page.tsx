import { SupportHub } from "@/components/support/SupportHub";
import { isDeveloper } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { listFaqs } from "@/lib/support";

export const metadata = { title: "Support" };

/** Support: FAQs, the form to raise a ticket, and tickets (a developer sees everyone's). `?tab=tickets` opens on them. */
export default async function SupportPage({ searchParams }: { searchParams: Promise<{ tab?: string }> }) {
  const user = await requireProfile();
  const { tab } = await searchParams;
  const dev = isDeveloper(user);
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-lg font-semibold">Support</h1>
        <p className="text-sm text-snow-faint">{dev ? "Answer tickets as Support, and keep the FAQs up to date." : "Find an answer, or ask Support."}</p>
      </div>
      <SupportHub
        faqs={await listFaqs()}
        prefill={{ name: user.name ?? "", email: user.email, phone: user.phone ?? "" }}
        signedIn
        isDev={dev}
        start={tab === "tickets" || tab === "form" ? tab : dev ? "tickets" : "faqs"}
      />
    </div>
  );
}
