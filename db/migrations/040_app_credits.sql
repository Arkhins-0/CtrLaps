-- The people behind the app, for About → Developers / Helpers. Set only in the database (scripts/credits.mjs or
-- Neon's table editor): nobody edits it from the app or the website.
CREATE TABLE app_credits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  -- The line under the name: "Owner & Maintainer".
  subtext text NOT NULL DEFAULT '',
  -- Where tapping the card goes (a GitHub profile, a website); blank for none.
  link text NOT NULL DEFAULT '',
  -- A picture by URL (e.g. https://github.com/<user>.png); blank shows initials.
  photo_url text NOT NULL DEFAULT '',
  position integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
