package dev.fixflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"})
class Notifications {
    record IncomingEvent(UUID eventId, UUID ticketId, String message) {}
    record Notification(UUID eventId, String message, OffsetDateTime receivedAt) {}

    private final JdbcClient db;
    private final ObjectMapper json;

    Notifications(JdbcClient db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @RabbitListener(queues = NotificationApplication.QUEUE)
    public void receive(Message message) throws IOException {
        IncomingEvent event = json.readValue(message.getBody(), IncomingEvent.class);
        db.sql("INSERT INTO notifications (event_id, ticket_id, message) VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING")
                .params(event.eventId(), event.ticketId(), event.message()).update();
    }

    @GetMapping("/api/notifications/{ticketId}")
    public List<Notification> list(@PathVariable UUID ticketId) {
        return db.sql("SELECT event_id, message, received_at FROM notifications WHERE ticket_id = ? ORDER BY received_at, event_id")
                .param(ticketId).query(Notification.class).list();
    }
}
