CREATE TABLE IF NOT EXISTS inbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    schema_version INTEGER NOT NULL
);

ALTER TABLE inbox_events ADD COLUMN IF NOT EXISTS event_type VARCHAR(50);
ALTER TABLE inbox_events ADD COLUMN IF NOT EXISTS schema_version INTEGER;

UPDATE inbox_events
SET event_type = 'LEGACY', schema_version = 1
WHERE event_type IS NULL OR schema_version IS NULL;

ALTER TABLE inbox_events ALTER COLUMN event_type SET NOT NULL;
ALTER TABLE inbox_events ALTER COLUMN schema_version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_inbox_events_event_id ON inbox_events (event_id);
