-- Support: FAQs, and tickets that people raise and developers answer (see support.ts).
-- A ticket's chat is a conversation of kind 'support'. Developers show in it only as "Support".

CREATE TABLE faqs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  category text NOT NULL,
  question text NOT NULL,
  answer text NOT NULL,
  -- The steps of a procedure, in order (none for a plain answer).
  steps text[] NOT NULL DEFAULT '{}',
  position int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_kind_check;
ALTER TABLE conversations ADD CONSTRAINT conversations_kind_check CHECK (kind IN ('channel', 'direct', 'group', 'category', 'support'));

CREATE TABLE support_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  -- Shown as #0001.
  number serial UNIQUE,
  -- Who raised it; null when raised before signing in (it is then theirs once they sign in with that email).
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  name text NOT NULL,
  email text NOT NULL,
  phone text,
  category text NOT NULL,
  subject text NOT NULL,
  details text NOT NULL,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  closed_at timestamptz,
  closed_by uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX support_tickets_user_idx ON support_tickets (user_id);
CREATE INDEX support_tickets_email_idx ON support_tickets (lower(email));
CREATE INDEX support_tickets_status_idx ON support_tickets (status, created_at DESC);
CREATE UNIQUE INDEX support_tickets_conversation_idx ON support_tickets (conversation_id);

-- The common questions, to start with; developers edit them in the app or on the website.
INSERT INTO faqs (category, question, answer, steps, position) VALUES
('Account & sign-in', 'How do I create an account?',
 'Anyone can register with their email. Your account is made once you confirm the email; an organiser then gives you a role.',
 ARRAY['On the sign-in screen, tap Create an account.', 'Enter your email and tap Send link.', 'Open the link in the email within 24 hours.', 'Choose a password, accept the Terms, then fill in your profile.'], 1),
('Account & sign-in', 'I forgot my password.',
 'You can choose a new password with a link sent to your email. The link works for 2 hours.',
 ARRAY['On the sign-in screen, tap Forgot password.', 'Enter your account''s email and send.', 'Open the link in the email and choose a new password.'], 2),
('Account & sign-in', 'How do I change my email or details?',
 'If you have no role yet, or you are an admin, you change them yourself under Account. Otherwise your manager or an admin changes them for you.',
 ARRAY['Open the Account tab, then Account.', 'Tap Edit profile to change your name, contact, date of birth or photo.', 'Under Change email, enter the new address and tap Send link; your email changes once you open the link.'], 3),
('Account & sign-in', 'Why can''t I see the Chats tab?',
 'People who registered themselves start as a User, who reads announcements and race-weekend channels but has no chats. Chats come with a role given by an organiser.',
 '{}', 4),
('Notifications', 'I am not getting notifications.',
 'Notifications need permission, and some phones stop apps in the background to save battery.',
 ARRAY['Open Account, then Settings, then Permissions.', 'Make sure Notifications, Run in background and Autostart show Allowed.', 'Tap any line that says Not allowed and allow it.'], 5),
('App problem', 'How do I update the app?',
 'The app tells you when a new version is out, and updates itself.',
 ARRAY['Open Account, then About.', 'Tap the version card to check for updates.', 'Tap Update app, and allow installing from CTR[L]APS if your phone asks.'], 6),
('App problem', 'The app is using a lot of storage.',
 'Photos, documents and voice notes from chats are kept on your phone so they open without signal. You can clear them and choose what downloads by itself.',
 ARRAY['Open Account, then Storage.', 'Turn off Automatic downloads for the kinds you don''t want kept.', 'Tap Clear to remove what is kept now; it downloads again when you open it.'], 7),
('Chats & messages', 'Can I edit or delete a message I sent?',
 'Yes, within 2 hours of sending it.',
 ARRAY['Long-press the message.', 'Tap Edit or Delete in the bar at the top.'], 8),
('Schedule & results', 'Where are the results and standings?',
 'Each race category has its results and standings for the season.',
 ARRAY['Open the Schedule tab.', 'Tap the trophy on the season card.', 'Pick a category to see drivers'' and teams'' points, and tap a session for its results.'], 9),
('Schedule & results', 'Why does the schedule show only some sessions?',
 'The schedule opens on Mine: the sessions of your own race categories. Tap All, or a category, to see the others.',
 '{}', 10);
