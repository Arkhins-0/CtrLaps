-- Results of a race category's sessions, entered on the website; standings are
-- worked out from them (see results.ts). A driver need not have an account.

CREATE TABLE session_results (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES race_sessions(id) ON DELETE CASCADE,
  row_order int NOT NULL,
  position int CHECK (position IS NULL OR position > 0),
  status text NOT NULL DEFAULT 'finished' CHECK (status IN ('finished', 'dnf', 'dns', 'dsq')),
  car_number text NOT NULL DEFAULT '',
  driver_name text NOT NULL,
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  team_id uuid REFERENCES teams(id) ON DELETE SET NULL,
  points numeric(6, 2) NOT NULL DEFAULT 0,
  best_lap text NOT NULL DEFAULT ''
);
CREATE INDEX session_results_session_idx ON session_results (session_id, row_order);
CREATE INDEX session_results_user_idx ON session_results (user_id);
