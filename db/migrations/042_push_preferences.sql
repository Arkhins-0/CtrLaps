-- Which notifications a person hears about (pushPrefs.ts): chats, announcements, channel posts, results. A row only
-- when it differs from on; urgent messages always come.
CREATE TABLE push_preferences (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind text NOT NULL,
  enabled boolean NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kind)
);
