-- Volunteer groups (volunteers.ts): a named group of volunteers with a lead coordinator, and a group chat for it
-- (conversations.kind = 'group'). Its members follow the group: its volunteers, its lead coordinator and every admin;
-- nobody is removed from it or leaves it while in the group. A member can be kept from sending (`no_messages`) or
-- from doing anything (`read_only`). The lead coordinator or an admin closes the chat (read-only for everyone).
CREATE TABLE volunteer_groups (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  coordinator_id uuid REFERENCES users(id) ON DELETE SET NULL,
  conversation_id uuid NOT NULL UNIQUE REFERENCES conversations(id) ON DELETE CASCADE,
  open boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX volunteer_groups_coordinator_idx ON volunteer_groups (coordinator_id);

ALTER TABLE users ADD COLUMN volunteer_group_id uuid REFERENCES volunteer_groups(id) ON DELETE SET NULL;
CREATE INDEX users_volunteer_group_idx ON users (volunteer_group_id);

-- What a member of a group chat may do: everything, everything but sending, or only read.
ALTER TABLE group_members ADD COLUMN permission text NOT NULL DEFAULT 'full' CHECK (permission IN ('full', 'no_messages', 'read_only'));

-- A starting group for every coordinator who has volunteers today ("<name>'s volunteers"), with its chat and members.
DO $$
DECLARE
  co record;
  conv uuid;
  grp uuid;
BEGIN
  FOR co IN
    SELECT DISTINCT p.id, COALESCE(NULLIF(p.name, ''), p.email) AS name
    FROM users v JOIN users p ON p.id = v.parent_id
    WHERE v.role = 'volunteer' AND p.role = 'coordinator' AND v.status <> 'deleted'
  LOOP
    INSERT INTO conversations (kind, name, created_by) VALUES ('group', co.name || '''s volunteers', co.id) RETURNING id INTO conv;
    INSERT INTO volunteer_groups (name, coordinator_id, conversation_id) VALUES (co.name || '''s volunteers', co.id, conv) RETURNING id INTO grp;
    UPDATE users SET volunteer_group_id = grp WHERE role = 'volunteer' AND parent_id = co.id AND status <> 'deleted';
    INSERT INTO group_members (conversation_id, user_id, role)
      SELECT conv, u.id, CASE WHEN u.role = 'volunteer' THEN 'member' ELSE 'admin' END
      FROM users u
      WHERE u.status = 'active' AND (u.volunteer_group_id = grp OR u.id = co.id OR u.role = 'admin')
      ON CONFLICT DO NOTHING;
  END LOOP;
END $$;
