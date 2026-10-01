-- Standings read a category's sessions in time order, and a driver's latest car number from their results.

CREATE INDEX IF NOT EXISTS race_sessions_category_idx ON race_sessions (category_id, starts_at);
CREATE INDEX IF NOT EXISTS session_results_user_session_idx ON session_results (user_id, session_id);
