-- A race weekend can have a photo (the track): it fades into Home's next-race card and heads the weekend's page.
ALTER TABLE race_weekends ADD COLUMN photo_key text;
