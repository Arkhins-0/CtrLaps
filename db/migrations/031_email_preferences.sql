-- Email preferences per account (see emailPrefs.ts): which kinds of email a person gets, and which race categories'
-- results. Only choices that differ from the default are stored: no row means the default (on; results for one's own
-- categories). Account and security mails, and the organisers' Email page, always go.

CREATE TABLE email_preferences (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind text NOT NULL,
  enabled boolean NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kind)
);

CREATE TABLE result_subscriptions (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  enabled boolean NOT NULL,
  PRIMARY KEY (user_id, category_id)
);
CREATE INDEX result_subscriptions_category_idx ON result_subscriptions (category_id);

-- The key in each mail's "Stop these emails" link: it names the account without signing anyone in.
ALTER TABLE users ADD COLUMN mail_token uuid NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX users_mail_token_idx ON users (mail_token);

-- When a session's results were first sent to the people who follow them ("Publish & notify").
ALTER TABLE race_sessions ADD COLUMN results_notified_at timestamptz;
