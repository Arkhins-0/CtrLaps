-- Delegations (volunteers.ts): the same kind of group as a volunteer group, for delegates (role race_official) —
-- JK Tyre, FMSCI and the like. A delegation has no lead coordinator: every admin and coordinator is in its chat and
-- manages it. A delegate's group is users.volunteer_group_id, as a volunteer's is (a person is one or the other).
ALTER TABLE volunteer_groups ADD COLUMN kind text NOT NULL DEFAULT 'volunteer' CHECK (kind IN ('volunteer', 'delegation'));
CREATE INDEX volunteer_groups_kind_idx ON volunteer_groups (kind);

-- Delegates no longer look after race categories (results are entered by admins and coordinators).
DELETE FROM category_people cp USING users u WHERE u.id = cp.user_id AND u.role = 'race_official';
