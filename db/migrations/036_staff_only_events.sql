-- Event lines in a volunteer group's chat about managing people (someone kept from sending, moved, taken out) are for
-- the chat's admins (the lead coordinator and admins) only; volunteers don't see them.
ALTER TABLE messages ADD COLUMN staff_only boolean NOT NULL DEFAULT false;
