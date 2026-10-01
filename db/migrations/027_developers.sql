-- Developers: admins who also answer support tickets (see support.ts). Everywhere else
-- they show as "Developer"; in support they show only as "Support". Set with
-- scripts/make-dev.mjs, or by another developer from the person's page.

ALTER TABLE users ADD COLUMN is_dev boolean NOT NULL DEFAULT false;
