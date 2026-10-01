import Image from "next/image";
import Link from "next/link";
import { redirect } from "next/navigation";
import { SupportHub } from "@/components/support/SupportHub";
import { currentUser } from "@/lib/auth";
import { APP_NAME } from "@/lib/config";
import { listFaqs } from "@/lib/support";

export const metadata = { title: "Help" };
export const dynamic = "force-dynamic";

/** Help for someone not signed in (the sign-in page's "Need help?"): the FAQs, and the form to ask Support. */
export default async function Help() {
  if (await currentUser()) redirect("/support");
  return (
    <main className="mx-auto w-full max-w-2xl space-y-5 px-4 py-8">
      <div className="flex items-center gap-3">
        <Image src="/logo.png" alt="" width={40} height={39} priority />
        <span className="text-xl font-bold tracking-tight">{APP_NAME}</span>
        <Link href="/" className="btn-ghost ml-auto px-4 py-1.5 text-xs">
          Sign in
        </Link>
      </div>
      <div>
        <h1 className="text-lg font-semibold">Help</h1>
        <p className="text-sm text-snow-faint">Find an answer, or ask Support. Replies come by email.</p>
      </div>
      <SupportHub faqs={await listFaqs()} prefill={{ name: "", email: "", phone: "" }} signedIn={false} />
    </main>
  );
}
