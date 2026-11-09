CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_tasks_official_title_trgm
    ON tasks USING gin(lower(title) gin_trgm_ops)
    WHERE type = 'OFFICIAL'