import { execFile } from "node:child_process";
import path from "node:path";
import { body, handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { isDeveloper } from "@/lib/roles";

export const dynamic = "force-dynamic";

/** What each button runs: scripts/seed-mock.mjs with this flag. */
const ACTIONS: Record<string, string[]> = { add: [], photos: ["--photos"], results: ["--results"], remove: ["--remove"] };

/**
 * Demo data from the debug app (Arkhime's demo-data idea): runs the mock-data script on this server. Only where
 * ALLOW_DEMO_DATA=1 is set (the local test server's .env.local, never the live site), and only for developers.
 * `{ action: "add" | "photos" | "results" | "remove" }` → `{ output }`, the script's last lines.
 */
export const POST = handle(async (request) => {
  if (process.env.ALLOW_DEMO_DATA !== "1") return fail("Not found.", 404);
  const user = await requireUser(["admin"]);
  if (!isDeveloper(user)) return fail("Not found.", 404);
  const b = await body(request);
  const flags = typeof b.action === "string" ? ACTIONS[b.action] : undefined;
  if (!flags) return fail("Choose add, photos, results or remove.");
  const script = path.join(process.cwd(), "scripts", "seed-mock.mjs");
  const output = await new Promise<string>((resolve) => {
    execFile(process.execPath, [script, ...flags], { cwd: process.cwd(), timeout: 180_000 }, (error, stdout, stderr) => {
      const lines = `${stdout}\n${stderr}`.split("\n").filter((l) => l.trim() && !/ssl|libpq|verify-full|trace-warnings|current behavior|See https|If you want/i.test(l));
      resolve((error && !lines.length ? error.message : lines.slice(-4).join("\n")).trim());
    });
  });
  return json({ output });
});
