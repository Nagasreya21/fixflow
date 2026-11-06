CREATE TABLE IF NOT EXISTS notifications (
    event_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    message TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ticket_notifications ON notifications (ticket_id, received_at);
