-- Channel notifications a person has muted (weekend and category channels): no push from them; posts still count as
-- unread. And category channel managers: coordinators an admin picks to post in a category's channel, as weekend
-- channels have had (channel_managers).
CREATE TABLE channel_mutes (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, conversation_id)
);
CREATE INDEX channel_mutes_conversation_idx ON channel_mutes (conversation_id);

CREATE TABLE category_managers (
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (category_id, user_id)
);
CREATE INDEX category_managers_user_idx ON category_managers (user_id);
