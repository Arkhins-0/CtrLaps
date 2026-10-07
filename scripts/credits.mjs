// The people shown in the app's About → Developers / Helpers (table app_credits, migration 040). Reads
// DATABASE_URL like migrate.mjs (.env.local, a test branch, before .env) and says which database it changes.
//
//   node scripts/credits.mjs list
//   node scripts/credits.mjs add --name "Arkhins-0" --subtext "Owner & Maintainer" \
//        --link https://github.com/Arkhins-0 --photo https://github.com/Arkhins-0.png [--position 0]
//   node scripts/credits.mjs edit <id> --subtext "Lead developer"      (any of the fields above)
//   node scripts/credits.mjs remove <id>
import path from "node:path";
import pg from "pg";

for (const file of [".env.local", ".env"]) {
  try {
    process.loadEnvFile(path.resolve(file));
  } catch {
    // Not there: the next file, or the host's environment, carries it.
  }
}
const url = process.env.DATABASE_URL || process.env.DATABASE_URL_POOLED;
if (!url) {
  console.error("DATABASE_URL is not set.");
  process.exit(1);
}

const [command, ...rest] = process.argv.slice(2);
const flags = {};
const positional = [];
for (let i = 0; i < rest.length; i++) {
  if (rest[i].startsWith("--")) flags[rest[i].slice(2)] = rest[++i] ?? "";
  else positional.push(rest[i]);
}
const FIELDS = { name: "name", subtext: "subtext", link: "link", photo: "photo_url", position: "position" };

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: true } });
await client.connect();
console.log(`database: ${new URL(url).hostname}`);
try {
  if (command === "list") {
    const { rows } = await client.query("SELECT id, position, name, subtext, link, photo_url FROM app_credits ORDER BY position, created_at");
    if (rows.length === 0) console.log("No one yet.");
    for (const r of rows) console.log(`${r.id}  [${r.position}] ${r.name} — ${r.subtext}${r.link ? `  ${r.link}` : ""}${r.photo_url ? `  (photo)` : ""}`);
  } else if (command === "add") {
    if (!flags.name) throw new Error("--name is needed.");
    const { rows } = await client.query(
      "INSERT INTO app_credits (name, subtext, link, photo_url, position) VALUES ($1, $2, $3, $4, $5) RETURNING id",
      [flags.name, flags.subtext ?? "", flags.link ?? "", flags.photo ?? "", Number(flags.position ?? 0)],
    );
    console.log(`Added ${flags.name}: ${rows[0].id}`);
  } else if (command === "edit") {
    const id = positional[0];
    const sets = Object.entries(FIELDS).filter(([flag]) => flag in flags);
    if (!id || sets.length === 0) throw new Error("Usage: edit <id> --name/--subtext/--link/--photo/--position …");
    const values = sets.map(([flag]) => (flag === "position" ? Number(flags[flag]) : flags[flag]));
    const { rowCount } = await client.query(
      `UPDATE app_credits SET ${sets.map(([, col], i) => `${col} = $${i + 2}`).join(", ")} WHERE id = $1`,
      [id, ...values],
    );
    console.log(rowCount ? "Changed." : "No one with that id.");
  } else if (command === "remove") {
    const { rowCount } = await client.query("DELETE FROM app_credits WHERE id = $1", [positional[0]]);
    console.log(rowCount ? "Removed." : "No one with that id.");
  } else {
    console.log("Commands: list, add, edit <id>, remove <id> (see the top of this file).");
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await client.end();
}
