// Fills the database with mock data to try the app with: people of every role (emails end in
// @mock.ctrlaps.test), two more race weekends with sessions and results, announcements, weekend and
// category channel posts, private chats and a group with the admin arkhins@arkhins.com.
// Everything it adds is recorded in audit_log ("mock.seed"), so it can all be taken out again.
// Usage: node scripts/seed-mock.mjs          (add; refuses if mock data is already there)
//        node scripts/seed-mock.mjs --remove (take it all out)
import path from "node:path";
import { randomBytes, randomInt } from "node:crypto";
import pg from "pg";

for (const file of [".env.local", ".env"]) {
  try {
    process.loadEnvFile(path.resolve(file));
  } catch {
    // Not there.
  }
}
const url = process.env.DATABASE_URL || process.env.DATABASE_URL_POOLED;
if (!url) {
  console.error("DATABASE_URL is not set.");
  process.exit(1);
}
console.log(`database: ${new URL(url).hostname}`);

const DOMAIN = "@mock.ctrlaps.test";
const ADMIN_EMAIL = "arkhins@arkhins.com";
const ALPHABET = "ACDEFGHJKLMNPQRSTUVWXYZ2345679";
const code = () => {
  let s = "";
  for (let i = 0; i < 8; i++) s += ALPHABET[randomInt(ALPHABET.length)];
  return `${s.slice(0, 4)}-${s.slice(4)}`;
};
const ago = (hours) => new Date(Date.now() - hours * 3600_000);

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: false } });
await client.connect();
const q = async (sql, args = []) => (await client.query(sql, args)).rows;
const one = async (sql, args = []) => (await q(sql, args))[0];

async function remove() {
  const runs = await q("SELECT id, detail FROM audit_log WHERE action = 'mock.seed'");
  await client.query("BEGIN");
  for (const r of runs) {
    const d = r.detail ?? {};
    if (d.messages?.length) await q("DELETE FROM messages WHERE id = ANY($1::uuid[])", [d.messages]);
    if (d.conversations?.length) await q("DELETE FROM conversations WHERE id = ANY($1::uuid[])", [d.conversations]);
    if (d.weekends?.length) await q("DELETE FROM race_weekends WHERE id = ANY($1::uuid[])", [d.weekends]);
  }
  const mock = (await q("SELECT id FROM users WHERE email LIKE $1", [`%${DOMAIN}`])).map((u) => u.id);
  if (mock.length) {
    await q("DELETE FROM messages WHERE sender_id = ANY($1::uuid[])", [mock]);
    await q("DELETE FROM users WHERE id = ANY($1::uuid[])", [mock]);
  }
  await q("DELETE FROM audit_log WHERE action = 'mock.seed'");
  await client.query("COMMIT");
  console.log(`removed: ${mock.length} mock people and ${runs.length} seed run(s) of posts, chats and weekends`);
}

async function seed() {
  if (await one("SELECT 1 FROM users WHERE email LIKE $1", [`%${DOMAIN}`])) {
    console.error("Mock data is already there. Run with --remove first.");
    process.exit(1);
  }
  const admin = await one("SELECT id, name FROM users WHERE email = $1", [ADMIN_EMAIL]);
  if (!admin) throw new Error(`No ${ADMIN_EMAIL} account.`);
  const coordinator = await one("SELECT id FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY created_at LIMIT 1");
  const season = await one("SELECT id FROM seasons WHERE status = 'active' ORDER BY created_at DESC LIMIT 1");
  const cat = Object.fromEntries((await q("SELECT id, code FROM categories WHERE season_id = $1", [season.id])).map((c) => [c.code, c.id]));
  // A team entered in a category, for racers and crew who race there.
  const teamIn = async (codeName, skip = 0) =>
    one(
      `SELECT t.id, t.name FROM teams t JOIN team_entries te ON te.team_id = t.id WHERE te.category_id = $1 ORDER BY t.name OFFSET $2 LIMIT 1`,
      [cat[codeName], skip],
    );
  const itcTeam = (await teamIn("ITC")) ?? (await one("SELECT id, name FROM teams ORDER BY name LIMIT 1"));
  const itcTeam2 = (await teamIn("ITC", 1)) ?? itcTeam;
  const lgbTeam = (await teamIn("LGB1300")) ?? itcTeam;

  const made = { messages: [], conversations: [], weekends: [] };
  await client.query("BEGIN");

  // ── People ──
  const people = {};
  const person = async (key, name, role, team = null, extra = {}) => {
    const u = await one(
      `INSERT INTO users (email, role, parent_id, team_name, team_id, status, verify_code, qr_token, name, phone, dob,
                          profile_completed_at, created_by, terms_accepted_at, terms_version, created_at)
       VALUES ($1, $2, $3, $4, $5, 'active', $6, $7, $8, $9, $10, now(), $3, now(), '2026-09-28', $11) RETURNING id`,
      [
        `${key}${DOMAIN}`, role, extra.parent ?? admin.id, team?.name ?? null, team?.id ?? null, code(),
        randomBytes(24).toString("base64url"), name, `+91 98${randomInt(10000000, 99999999)}`, extra.dob ?? "1994-05-12", ago(24 * 20),
      ],
    );
    people[key] = u.id;
    return u.id;
  };
  await person("priya", "Priya Raman", "race_official");
  await person("sanjay", "Sanjay Iyer", "race_official");
  await person("arjun", "Arjun Menon", "team_manager", itcTeam);
  await person("karthik", "Karthik Subramanian", "racer", itcTeam, { parent: people.arjun });
  await person("divya", "Divya Krishnan", "crew", itcTeam, { parent: people.arjun });
  await person("rahul", "Rahul Nair", "racer", lgbTeam);
  await person("aditya", "Aditya Rao", "racer", itcTeam2);
  await person("vikram", "Vikram Singh", "security_head");
  await person("anand", "Anand Kumar", "security", null, { parent: people.vikram });
  await person("meera", "Meera Das", "volunteer", null, { parent: coordinator?.id ?? admin.id });
  await person("neha", "Neha Joseph", "user", null, { parent: null });
  // Priya looks after ITC and ISTC; Sanjay, given none, looks after every class.
  for (const c of ["ITC", "ISTC"]) await q("INSERT INTO category_people (user_id, category_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [people.priya, cat[c]]);
  await q("INSERT INTO category_people (user_id, category_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [people.karthik, cat.ITC]);
  await q("INSERT INTO category_followers (user_id, category_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [people.neha, cat.F4]);

  const everyone = (await q("SELECT id FROM users WHERE status = 'active'")).map((u) => u.id);

  // ── Messages ──
  // A message, and who it reached; the admin has read all but the newest few.
  const post = async (conversationId, sender, body, hours, recipients, { urgent = false, unreadFor = [] } = {}) => {
    const at = ago(hours);
    const m = await one(
      `INSERT INTO messages (conversation_id, sender_id, body, urgent, season_id, created_at) VALUES ($1, $2, $3, $4, $5, $6) RETURNING id`,
      [conversationId, sender, body, urgent, season.id, at],
    );
    made.messages.push(m.id);
    const to = recipients.filter((id) => id !== sender);
    for (const id of to) {
      const read = !unreadFor.includes(id);
      await q("INSERT INTO message_recipients (message_id, user_id, delivered_at, read_at) VALUES ($1, $2, $3, $4)", [
        m.id, id, at, read ? new Date(at.getTime() + 10 * 60_000) : null,
      ]);
    }
    if (conversationId) await q("UPDATE conversations SET last_message_at = GREATEST(COALESCE(last_message_at, $2), $2) WHERE id = $1", [conversationId, at]);
    return m.id;
  };

  // Announcements from the admin.
  const staff = everyone.filter((id) => id !== admin.id && id !== people.neha);
  await post(null, admin.id, "Welcome to CTR[L]APS for the 2026 season. Keep the app open over race weekends: schedule changes, gate notices and results come here first.", 24 * 6, staff);
  await post(null, admin.id, "Paddock passes for Round 1 are ready. Collect them from the race office on Thursday between 10 am and 6 pm. Bring your ID card QR.", 30, staff);
  await post(null, admin.id, "Heavy rain is forecast for Saturday afternoon at Kari. Wet-weather procedures apply: pit lane speed 40 km/h, and all sessions may be delayed.", 3, staff, { urgent: true, unreadFor: [] });

  // ── Race weekends ──
  const weekend = async (name, venue, city, start, end, codes) => {
    const w = await one(
      `INSERT INTO race_weekends (name, venue, city, country, timezone, starts_on, ends_on, season_id) VALUES ($1, $2, $3, 'India', 'Asia/Kolkata', $4, $5, $6) RETURNING id`,
      [name, venue, city, start, end, season.id],
    );
    made.weekends.push(w.id);
    for (const c of codes) await q("INSERT INTO weekend_categories (weekend_id, category_id) VALUES ($1, $2)", [w.id, cat[c]]);
    return w.id;
  };
  const session = async (weekendId, name, startsAt, minutes, codeName) =>
    (
      await one(
        `INSERT INTO race_sessions (weekend_id, name, starts_at, ends_at, category_id) VALUES ($1, $2, $3, $4, $5) RETURNING id`,
        [weekendId, name, startsAt, new Date(new Date(startsAt).getTime() + minutes * 60_000), codeName ? cat[codeName] : null],
      )
    ).id;

  const opener = await weekend("Season opener · Chennai", "Madras International Circuit", "Chennai", "2026-09-19", "2026-09-20", ["ITC", "LGB1300", "F4"]);
  await session(opener, "Drivers' briefing", "2026-09-19T03:00:00Z", 45, null);
  await session(opener, "ITC Qualifying", "2026-09-19T05:00:00Z", 20, "ITC");
  const itcRace = await session(opener, "ITC Race 1", "2026-09-19T09:30:00Z", 25, "ITC");
  const lgbRace = await session(opener, "LGB1300 Race 1", "2026-09-20T05:00:00Z", 20, "LGB1300");
  const f4Race = await session(opener, "F4 Race 1", "2026-09-20T08:00:00Z", 25, "F4");
  const round2 = await weekend("Round 2 · Coimbatore", "Kari Motor Speedway", "Coimbatore", "2026-11-13", "2026-11-15", ["ITC", "ISTC", "LGB1300"]);
  await session(round2, "Free practice", "2026-11-13T04:30:00Z", 30, "ITC");
  await session(round2, "ITC Qualifying", "2026-11-14T05:00:00Z", 20, "ITC");
  await session(round2, "ISTC Race 1", "2026-11-14T09:00:00Z", 25, "ISTC");
  await session(round2, "LGB1300 Race 1", "2026-11-15T05:30:00Z", 20, "LGB1300");

  // Results of the opener.
  const teams = (await q("SELECT id, name FROM teams ORDER BY name")).map((t) => t.id);
  const results = async (sessionId, rows) => {
    for (const [i, r] of rows.entries()) {
      await q(
        `INSERT INTO session_results (session_id, row_order, position, status, car_number, driver_name, user_id, team_id, points, best_lap)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
        [sessionId, i, r.status ? null : i + 1, r.status ?? "finished", r.car, r.name, r.user ?? null, r.team ?? null, r.pts ?? 0, r.lap ?? ""],
      );
    }
  };
  await results(itcRace, [
    { car: "7", name: "Karthik Subramanian", user: people.karthik, team: itcTeam.id, pts: 25, lap: "1:48.215" },
    { car: "21", name: "Aditya Rao", user: people.aditya, team: itcTeam2.id, pts: 18, lap: "1:48.602" },
    { car: "11", name: "Rohan Pillai", team: teams[2], pts: 15, lap: "1:49.010" },
    { car: "3", name: "Farhan Ali", team: teams[5], pts: 12, lap: "1:49.377" },
    { car: "44", name: "Siddharth Gupta", team: itcTeam.id, pts: 10, lap: "1:49.880" },
    { car: "19", name: "Harish Varma", team: teams[8], status: "dnf" },
  ]);
  await results(lgbRace, [
    { car: "12", name: "Rahul Nair", user: people.rahul, team: lgbTeam.id, pts: 25, lap: "1:58.441" },
    { car: "5", name: "Ishaan Mehta", team: teams[3], pts: 18, lap: "1:58.903" },
    { car: "27", name: "Varun Shetty", team: teams[6], pts: 15, lap: "1:59.120" },
    { car: "9", name: "Nikhil Reddy", team: lgbTeam.id, pts: 12, lap: "1:59.664" },
  ]);
  await results(f4Race, [
    { car: "1", name: "Ananya Sharma", team: teams[1], pts: 25, lap: "1:44.902" },
    { car: "14", name: "Kabir Joshi", team: teams[4], pts: 18, lap: "1:45.118" },
    { car: "8", name: "Tanvi Kulkarni", team: teams[7], status: "dsq" },
  ]);

  // ── Weekend channels ──
  const channel = async (weekendId) => {
    const found = await one("SELECT id FROM conversations WHERE kind = 'channel' AND weekend_id = $1", [weekendId]);
    if (found) return found.id;
    const c = await one("INSERT INTO conversations (kind, weekend_id) VALUES ('channel', $1) RETURNING id", [weekendId]);
    return c.id;
  };
  const round1 = await one("SELECT id FROM race_weekends WHERE season_id = $1 AND NOT (id = ANY($2::uuid[])) ORDER BY starts_on LIMIT 1", [season.id, made.weekends]);
  const openerChannel = await channel(opener);
  await post(openerChannel, admin.id, "Scrutineering opens at 7 am on Saturday in the technical bay behind pit 12.", 24 * 11, everyone);
  await post(openerChannel, coordinator?.id ?? admin.id, "Results of all races are now on Standings. Thanks everyone for a smooth opener!", 24 * 9, everyone);
  if (round1) {
    const r1 = await channel(round1.id);
    await post(r1, admin.id, "Gate 2 opens at 6:30 am for team vehicles. Paddock parking is by pass colour only.", 20, everyone);
    await post(r1, coordinator?.id ?? admin.id, "Drivers' briefing is at 8:30 am in the media centre, first floor. Attendance is compulsory.", 6, everyone);
    await post(r1, admin.id, "Medical centre is next to race control. In an emergency call the marshal post on channel 3.", 1.5, everyone, { unreadFor: [admin.id] });
  }

  // ── Category channels ──
  const categoryChannel = async (codeName) => {
    const found = await one("SELECT id FROM conversations WHERE kind = 'category' AND category_id = $1", [cat[codeName]]);
    if (found) return found.id;
    const c = await one("INSERT INTO conversations (kind, category_id) VALUES ('category', $1) RETURNING id", [cat[codeName]]);
    made.conversations.push(c.id);
    return c.id;
  };
  const itc = await categoryChannel("ITC");
  const itcPeople = [admin.id, coordinator?.id, people.priya, people.sanjay, people.arjun, people.karthik, people.divya, people.aditya].filter(Boolean);
  await post(itc, people.priya, "ITC: minimum weight after qualifying is 1,080 kg with driver. Cars will be weighed at random.", 24 * 3, itcPeople);
  await post(itc, people.priya, "Tyre allocation for Round 1: 4 new slicks and 4 wets per car. Tyre marking on Friday 4–6 pm.", 18, itcPeople);
  await post(itc, coordinator?.id ?? admin.id, "ITC teams: please send your driver change forms to race control by Thursday night.", 2, itcPeople, { unreadFor: [admin.id] });
  const lgb = await categoryChannel("LGB1300");
  const lgbPeople = [admin.id, coordinator?.id, people.sanjay, people.rahul].filter(Boolean);
  await post(lgb, people.sanjay, "LGB1300 grid for Race 1 will be by qualifying times. Grid forms at 11 am on Saturday.", 26, lgbPeople);
  const f4 = await categoryChannel("F4");
  await post(f4, admin.id, "F4 drivers: data loggers must be returned to the scrutineering bay after every session.", 40, [admin.id, coordinator?.id, people.sanjay, people.neha].filter(Boolean));

  // ── Private chats with the admin ──
  const direct = async (otherKey) => {
    const other = people[otherKey];
    const [a, b] = [admin.id, other].sort();
    const c = await one("INSERT INTO conversations (kind, owner_id, member_id) VALUES ('direct', $1, $2) RETURNING id", [a, b]);
    return c.id;
  };
  const chat = async (otherKey, lines) => {
    const c = await direct(otherKey);
    for (const [who, text, hours, unread] of lines) {
      const sender = who === "me" ? admin.id : people[otherKey];
      const to = who === "me" ? people[otherKey] : admin.id;
      await post(c, sender, text, hours, [to], { unreadFor: unread ? [to] : [] });
    }
  };
  await chat("arjun", [
    ["them", "Hi, this is Arjun from the ITC team. Can we get two extra paddock passes for our data engineers?", 28],
    ["me", "Hi Arjun. Yes, send me their names and ID numbers and I'll add them.", 27.5],
    ["them", "Sent to your email just now. Thanks!", 27],
    ["me", "Done. They can collect them at the race office on Thursday.", 26],
    ["them", "Perfect 👍", 25.8],
    ["them", "Also, is the pit garage allocation final?", 1, true],
  ]);
  await chat("priya", [
    ["me", "Priya, can you share the updated ITC supplementary regulations in the category channel?", 22],
    ["them", "Sure, posting it this evening after the stewards sign off.", 21.5],
    ["them", "Stewards meeting moved to 5 pm today, FYI.", 4, true],
  ]);
  await chat("vikram", [
    ["them", "Gate 1 scanner is working fine now. We checked 120 passes this morning.", 9],
    ["me", "Great. Keep one volunteer at Gate 2 till noon please.", 8.5],
    ["them", "Anand is there.", 8.4],
  ]);
  await chat("karthik", [
    ["them", "Sir, my licence renewal is still pending with FMSCI. Can I still take part in qualifying?", 50],
    ["me", "Yes, carry the renewal receipt. Race control will verify it at scrutineering.", 49],
    ["them", "Thank you!", 48.9],
  ]);

  // ── A group ──
  const group = await one(
    "INSERT INTO conversations (kind, name, created_by) VALUES ('group', 'Race control · Round 1', $1) RETURNING id",
    [admin.id],
  );
  made.conversations.push(group.id);
  const members = [admin.id, coordinator?.id, people.priya, people.sanjay, people.vikram].filter(Boolean);
  for (const id of members) {
    await q("INSERT INTO group_members (conversation_id, user_id, role, joined_at) VALUES ($1, $2, $3, $4)", [group.id, id, id === admin.id ? "admin" : "member", ago(72)]);
  }
  await post(group.id, admin.id, "Created this group for race control over Round 1.", 72, members);
  await post(group.id, people.sanjay, "Marshal posts 1 to 9 confirmed for Saturday.", 30, members);
  await post(group.id, people.vikram, "Security briefing at 6 am at Gate 1 for all marshals and security.", 12, members);
  await post(group.id, people.priya, "Timing and scoring test done, all transponders reading.", 0.8, members, { unreadFor: [admin.id] });

  await q("INSERT INTO audit_log (actor_id, action, detail) VALUES ($1, 'mock.seed', $2)", [admin.id, JSON.stringify(made)]);
  await client.query("COMMIT");
  console.log(`added: ${Object.keys(people).length} mock people, ${made.weekends.length} weekends, ${made.messages.length} messages`);
}

try {
  if (process.argv.includes("--remove")) await remove();
  else await seed();
} catch (error) {
  await client.query("ROLLBACK").catch(() => {});
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await client.end();
}
