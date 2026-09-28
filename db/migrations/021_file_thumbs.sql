-- A photo's tiny preview (a JPEG of about 24 px, base64), sent by whoever
-- uploads it. A phone that has not downloaded the photo shows it blurred.

ALTER TABLE files ADD COLUMN thumb text;
