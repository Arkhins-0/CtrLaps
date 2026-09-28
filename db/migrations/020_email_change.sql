-- Changing an account's email: a link goes to the new address, and the
-- change is made only when it is opened. The address waits on the link.

ALTER TABLE auth_tokens DROP CONSTRAINT IF EXISTS auth_tokens_kind_check;
ALTER TABLE auth_tokens ADD CONSTRAINT auth_tokens_kind_check CHECK (kind IN ('invite', 'reset', 'email'));
ALTER TABLE auth_tokens ADD COLUMN new_email text;
