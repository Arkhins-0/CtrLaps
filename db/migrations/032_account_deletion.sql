-- Deleting an account (see accountDeletion.ts): asked for by the person (or by a developer for them), it waits 7 days
-- (signing in again cancels it), then everything personal is erased. The row stays, as an empty "Deleted user", so
-- the conversations and posts other people rely on keep their shape; it can never sign in again.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('pending', 'active', 'suspended', 'dismissed', 'banned', 'deleted'));

-- When the erasure is due (set while waiting), who asked, and when it was done.
ALTER TABLE users ADD COLUMN deletion_due_at timestamptz;
ALTER TABLE users ADD COLUMN deletion_requested_by uuid REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN deleted_at timestamptz;
CREATE INDEX users_deletion_due_idx ON users (deletion_due_at) WHERE deletion_due_at IS NOT NULL;
