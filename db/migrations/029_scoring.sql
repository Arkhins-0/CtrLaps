-- Points tables (see scoring.ts). A category may have one: points by finishing position, for DNF / DNS / DSQ, and
-- bonuses for pole and fastest lap. A session scores from it or not (null: by its name, so qualifying and practice
-- don't). A result row's points come from the table unless typed by hand (manual_points).

ALTER TABLE categories ADD COLUMN scoring jsonb;
ALTER TABLE race_sessions ADD COLUMN scores boolean;
ALTER TABLE session_results ADD COLUMN pole boolean NOT NULL DEFAULT false;
ALTER TABLE session_results ADD COLUMN fastest_lap boolean NOT NULL DEFAULT false;
-- Results saved before points tables were all typed by hand.
ALTER TABLE session_results ADD COLUMN manual_points boolean NOT NULL DEFAULT true;
