import Link from "next/link";
import { notFound } from "next/navigation";
import { TicketView } from "@/components/support/TicketView";
import { AuthError } from "@/lib/auth";
import { requireProfile } from "@/lib/session";
import { ticketView } from "@/lib/support";

export const metadata = { title: "Ticket" };

/** One support ticket and its chat. */
export default async function TicketPage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const view = await ticketView(user, id).catch((error) => {
    if (error instanceof AuthError) return null;
    throw error;
  });
  if (!view) notFound();
  return (
    <div className="space-y-4">
      <Link href="/support?tab=tickets" className="text-xs text-snow-faint hover:text-snow">
        ← Tickets
      </Link>
      <TicketView initial={view} />
    </div>
  );
}
