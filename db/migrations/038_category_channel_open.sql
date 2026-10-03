-- A race category's channel can be closed and reopened by an admin, as a weekend's can (it also closes while its
-- season is archived).
ALTER TABLE categories ADD COLUMN channel_open boolean NOT NULL DEFAULT true;
