ALTER TABLE lobby_tasks
    ADD COLUMN description_snapshot TEXT;

UPDATE lobby_tasks AS lt
SET description_snapshot = t.description
    FROM tasks AS t
WHERE lt.task_id = t.id;

ALTER TABLE lobby_tasks
    ALTER COLUMN description_snapshot SET NOT NULL;