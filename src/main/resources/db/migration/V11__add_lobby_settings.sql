ALTER TABLE lobbies
    ADD COLUMN max_participants INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 30,
    ADD CONSTRAINT ck_lobbies_max_participants
        CHECK (max_participants BETWEEN 2 AND 20),
    ADD CONSTRAINT ck_lobbies_duration_minutes
        CHECK (duration_minutes BETWEEN 5 AND 120);