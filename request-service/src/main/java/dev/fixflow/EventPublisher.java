package dev.fixflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    record PendingEvent(UUID eventId, UUID ticketId, String message) {}

    private final JdbcClient db;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    EventPublisher(JdbcClient db, RabbitTemplate rabbit, ObjectMapper json) {
        this.db = db;
        this.rabbit = rabbit;
        this.json = json;
    }

    @Scheduled(fixedDelay = 2000)
    void publishPending() {
        var pending = db.sql("""
                SELECT event_id, ticket_id, message FROM ticket_events
                WHERE NOT published ORDER BY position LIMIT 25
                """).query(PendingEvent.class).list();

        for (PendingEvent event : pending) {
            try {
                var confirmation = new CorrelationData(event.eventId().toString());
                var message = MessageBuilder.withBody(json.writeValueAsBytes(event))
                        .setContentType("application/json")
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build();
                rabbit.send("", RequestApplication.QUEUE, message, confirmation);
                if (!confirmation.getFuture().get(5, TimeUnit.SECONDS).isAck()
                        || confirmation.getReturned() != null) {
                    log.warn("Event {} could not be routed. It remains pending.", event.eventId());
                    return;
                }
                db.sql("UPDATE ticket_events SET published = true WHERE event_id = ?").param(event.eventId()).update();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                log.warn("Event {} remains pending: {}", event.eventId(), failure.toString());
                return;
            }
        }
    }
}
