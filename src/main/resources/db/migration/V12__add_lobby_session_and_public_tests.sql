ALTER TABLE lobbies
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN ends_at TIMESTAMPTZ;

ALTER TABLE task_languages
    ADD COLUMN java_public_tests JSONB;

ALTER TABLE lobby_tasks
    ADD COLUMN java_public_tests JSONB,
    ADD COLUMN title_snapshot VARCHAR(200);

UPDATE lobby_tasks AS lt
SET title_snapshot = t.title
FROM tasks AS t
WHERE lt.task_id = t.id;

ALTER TABLE lobby_tasks
    ALTER COLUMN title_snapshot SET NOT NULL;
