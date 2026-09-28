-- Race categories (classes): several race each round, each with its own
-- sessions. A season has its categories; a weekend says which of them run
-- that round; a session may belong to one (none = for everyone: briefings,
-- prize giving).

CREATE TABLE categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  name text NOT NULL,
  code text NOT NULL,
  color text NOT NULL,
  position integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (season_id, code)
);
CREATE INDEX categories_season_idx ON categories (season_id, position);

CREATE TABLE weekend_categories (
  weekend_id uuid NOT NULL REFERENCES race_weekends(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  PRIMARY KEY (weekend_id, category_id)
);

ALTER TABLE race_sessions ADD COLUMN category_id uuid REFERENCES categories(id) ON DELETE SET NULL;

-- The 2026 CTR JK Tyre FMSCI Indian National Car Racing Championship, in the current season.
INSERT INTO categories (season_id, name, code, color, position)
SELECT s.id, v.name, v.code, v.color, v.position
FROM seasons s
CROSS JOIN (VALUES
  ('Formula LGB 1300', 'LGB1300', '#3B82F6', 0),
  ('LGB F4', 'F4', '#F97316', 1),
  ('Super Stock', 'SS', '#EC4899', 2),
  ('Indian Junior Touring Car', 'IJTC', '#14B8A6', 3),
  ('Indian Touring Car', 'ITC', '#A855F7', 4),
  ('Indian Super Touring Car', 'ISTC', '#84CC16', 5),
  ('Levitas Cup', 'LEV', '#D97706', 6)
) AS v(name, code, color, position)
WHERE s.is_current;
