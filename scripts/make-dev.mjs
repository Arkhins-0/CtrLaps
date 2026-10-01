// Make an admin a developer (they answer support tickets), or take it back.
// Usage: npm run make-dev -- someone@example.com          (make a developer)
//        npm run make-dev -- someone@example.com --remove (back to a plain admin)
import path from "node:path";
import pg from "pg";

for (const file of [".env.local", ".env"]) {
  try {
    process.loadEnvFile(path.resolve(file));
  } catch {
    // Not there: the next file, or the host's environment.
  }
}

const email = (process.argv[2] ?? "").trim().toLowerCase();
const remove = process.argv.includes("--remove");
if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
  console.error("Usage: npm run make-dev -- someone@example.com [--remove]");
  process.exit(1);
}
const url = process.env.DATABASE_URL || process.env.DATABASE_URL_POOLED;
if (!url) {
  console.error("DATABASE_URL is not set.");
  process.exit(1);
}

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  console.log(`database: ${new URL(url).hostname}`);
  const { rows } = await client.query("SELECT id, role, is_dev FROM users WHERE email = $1", [email]);
  const user = rows[0];
  if (!user) {
    console.error(`${email} has no account.`);
    process.exitCode = 1;
  } else if (!remove && user.role !== "admin") {
    console.error(`${email} is ${user.role}, not an admin. Make them an admin first.`);
    process.exitCode = 1;
  } else if (user.is_dev === !remove) {
    console.log(`${email} ${remove ? "is not a developer" : "is already a developer"}; nothing to do`);
  } else {
    await client.query("UPDATE users SET is_dev = $2 WHERE id = $1", [user.id, !remove]);
    await client.query("INSERT INTO audit_log (actor_id, target_id, action, detail) VALUES (NULL, $1, $2, $3)", [
      user.id,
      remove ? "developer.removed" : "developer.made",
      JSON.stringify({ by: "script" }),
    ]);
    console.log(`${email} ${remove ? "is no longer a developer" : "is now a developer"}`);
  }
} finally {
  await client.end();
}
