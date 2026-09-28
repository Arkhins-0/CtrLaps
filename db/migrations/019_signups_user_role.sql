-- Anyone may register: the email is verified first through a link, and only
-- then is the account created, with the role 'user'. Drivers are now racers.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE users SET role = 'racer' WHERE role = 'driver';
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN (
  'admin', 'coordinator', 'race_official', 'team_manager', 'racer', 'crew',
  'security_head', 'security', 'volunteer', 'user'
));

-- A registration waiting for its email to be confirmed. No account exists
-- yet, so this cannot live in auth_tokens. Only the hash of the link is kept.
CREATE TABLE signups (
  hash text PRIMARY KEY,
  email text NOT NULL,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX signups_email_idx ON signups (email);
