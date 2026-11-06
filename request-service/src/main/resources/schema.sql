CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    priority TEXT NOT NULL,
    status TEXT NOT NULL,
    technician TEXT
);

CREATE TABLE IF NOT EXISTS ticket_events (
    position BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    status TEXT NOT NULL,
    message TEXT NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS pending_ticket_events ON ticket_events (position) WHERE NOT published;
CREATE INDEX IF NOT EXISTS ticket_timeline ON ticket_events (ticket_id, position);
