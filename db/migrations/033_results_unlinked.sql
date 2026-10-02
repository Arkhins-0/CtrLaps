-- A result whose racer deleted their account: unlinked from it, it stays a typed name and is never linked to another
-- racer of the same name (the late-entry rule skips it), so a namesake can't pick up those points.
ALTER TABLE session_results ADD COLUMN unlinked boolean NOT NULL DEFAULT false;
