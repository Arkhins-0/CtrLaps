-- People with no role yet (users) may follow race categories as fans: they read
-- those categories' channels and see their sessions as "Mine". See categoryChannels.ts.

CREATE TABLE category_followers (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, category_id)
);
CREATE INDEX category_followers_category_idx ON category_followers (category_id);
