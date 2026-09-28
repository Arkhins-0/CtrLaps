-- Teams as records, the categories each team is entered in, and the
-- categories a person races in (racers) or looks after (race officials).
-- users.team_name stays (older apps read it); users.team_id follows it.

CREATE TABLE teams (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX teams_name_idx ON teams (lower(name));

ALTER TABLE users ADD COLUMN team_id uuid REFERENCES teams(id) ON DELETE SET NULL;
CREATE INDEX users_team_idx ON users (team_id);

CREATE TABLE team_entries (
  team_id uuid NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  PRIMARY KEY (team_id, category_id)
);

CREATE TABLE category_people (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, category_id)
);
CREATE INDEX category_people_category_idx ON category_people (category_id);

-- The teams people already carry by name.
INSERT INTO teams (name)
SELECT DISTINCT ON (lower(trim(team_name))) trim(team_name) FROM users WHERE coalesce(trim(team_name), '') <> ''
ON CONFLICT DO NOTHING;

-- The 2026 entry list (CTR JK Tyre FMSCI Indian National Car Racing Championship).
INSERT INTO teams (name)
SELECT v.name FROM (VALUES
  ('Momentum Motorsports'), ('MSPORT'), ('Ahura Racing'), ('Avalanche Motorsports'), ('Delta Speeds'), ('Team M.A.R.S'),
  ('DTS Racing'), ('Dark Don Motorsport'), ('Team Performance Racing'), ('Red Line Racing'), ('Race Concept'),
  ('Zion Racing'), ('Arka Motorsports'), ('Team N1 Motorsports'), ('Levitas Racing')
) AS v(name)
ON CONFLICT DO NOTHING;

INSERT INTO team_entries (team_id, category_id)
SELECT t.id, c.id
FROM (VALUES
  ('Momentum Motorsports', 'LGB1300'), ('MSPORT', 'LGB1300'), ('Ahura Racing', 'LGB1300'), ('Avalanche Motorsports', 'LGB1300'),
  ('Delta Speeds', 'LGB1300'), ('Team M.A.R.S', 'LGB1300'), ('DTS Racing', 'LGB1300'),
  ('MSPORT', 'F4'), ('Ahura Racing', 'F4'), ('Avalanche Motorsports', 'F4'), ('Team M.A.R.S', 'F4'), ('Dark Don Motorsport', 'F4'),
  ('Team Performance Racing', 'SS'), ('Red Line Racing', 'SS'), ('DTS Racing', 'SS'), ('Race Concept', 'SS'), ('Zion Racing', 'SS'),
  ('Team Performance Racing', 'IJTC'), ('Red Line Racing', 'IJTC'), ('Race Concept', 'IJTC'), ('Arka Motorsports', 'IJTC'),
  ('Team N1 Motorsports', 'IJTC'),
  ('Team Performance Racing', 'ITC'), ('Arka Motorsports', 'ITC'), ('Team N1 Motorsports', 'ITC'), ('Race Concept', 'ITC'),
  ('Arka Motorsports', 'ISTC'), ('Team N1 Motorsports', 'ISTC'), ('Race Concept', 'ISTC'),
  ('Levitas Racing', 'LEV')
) AS v(team, code)
JOIN teams t ON lower(t.name) = lower(v.team)
JOIN categories c ON c.code = v.code
JOIN seasons s ON s.id = c.season_id AND s.is_current
ON CONFLICT DO NOTHING;

UPDATE users u SET team_id = t.id FROM teams t WHERE lower(trim(u.team_name)) = lower(t.name);
