-- A channel per race category, for the season the category belongs to
-- ("ITC 2026"). Its members follow from the entries: see categoryChannels.ts.

ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_kind_check;
ALTER TABLE conversations ADD CONSTRAINT conversations_kind_check CHECK (kind IN ('channel', 'direct', 'group', 'category'));
ALTER TABLE conversations ADD COLUMN category_id uuid REFERENCES categories(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX conversations_category_idx ON conversations (category_id) WHERE kind = 'category';
