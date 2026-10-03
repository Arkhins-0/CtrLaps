import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { dashboard } from "@/lib/dashboard";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** The Home card for admins and coordinators (null for anyone else): today's sessions, time changes, people to nudge. */
export const GET = handle(async () => json({ dashboard: await dashboard(await requireUser()) }));
