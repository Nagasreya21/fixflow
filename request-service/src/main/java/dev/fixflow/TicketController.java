package dev.fixflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tickets")
class TicketController {
    enum Category { WATER_LEAK, POWER_OUTAGE, APPLIANCE, OTHER }
    enum Status { OPEN, ASSIGNED, IN_PROGRESS, RESOLVED }

    record NewTicket(@NotBlank @Size(max = 120) String title, @NotNull Category category) {}
    record Assignment(@NotBlank @Size(max = 80) String technician) {}
    record StatusChange(@NotNull Status status) {}
    record Ticket(UUID id, String title, Category category, String priority, Status status, String technician) {}
    record Event(Status status, String message, OffsetDateTime happenedAt) {}

    private final JdbcClient db;

    TicketController(JdbcClient db) {
        this.db = db;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Ticket create(@Valid @RequestBody NewTicket input) {
        UUID id = UUID.randomUUID();
        String priority = switch (input.category()) {
            case WATER_LEAK, POWER_OUTAGE -> "URGENT";
            default -> "NORMAL";
        };
        db.sql("INSERT INTO tickets (id, title, category, priority, status) VALUES (?, ?, ?, ?, 'OPEN')")
                .params(id, input.title().trim(), input.category().name(), priority).update();
        addEvent(id, Status.OPEN, "Request received.");
        return get(id);
    }

    @GetMapping
    public List<Ticket> list() {
        return db.sql("""
                SELECT * FROM tickets
                ORDER BY (priority = 'URGENT') DESC, title, id
                """).query(Ticket.class).list();
    }

    @GetMapping("/{id}")
    public Ticket get(@PathVariable UUID id) {
        return db.sql("SELECT * FROM tickets WHERE id = ?").param(id).query(Ticket.class)
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    @GetMapping("/{id}/timeline")
    public List<Event> timeline(@PathVariable UUID id) {
        get(id);
        return db.sql("SELECT status, message, happened_at FROM ticket_events WHERE ticket_id = ? ORDER BY position")
                .param(id).query(Event.class).list();
    }

    @PutMapping("/{id}/assignment")
    @Transactional
    public Ticket assign(@PathVariable UUID id, @Valid @RequestBody Assignment input) {
        String technician = input.technician().trim();
        int updated = db.sql("UPDATE tickets SET status = 'ASSIGNED', technician = ? WHERE id = ? AND status = 'OPEN'")
                .params(technician, id).update();
        checkUpdate(id, updated);
        addEvent(id, Status.ASSIGNED, technician + " was assigned.");
        return get(id);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public Ticket changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChange input) {
        Status next = input.status();
        Status required = switch (next) {
            case IN_PROGRESS -> Status.ASSIGNED;
            case RESOLVED -> Status.IN_PROGRESS;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use assignment to assign a ticket");
        };
        int updated = db.sql("UPDATE tickets SET status = ? WHERE id = ? AND status = ?")
                .params(next.name(), id, required.name()).update();
        checkUpdate(id, updated);
        addEvent(id, next, next == Status.IN_PROGRESS ? "Repair started." : "Request resolved.");
        return get(id);
    }

    private void checkUpdate(UUID id, int updated) {
        if (updated == 0) {
            get(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket status has changed");
        }
    }

    private void addEvent(UUID id, Status status, String message) {
        db.sql("INSERT INTO ticket_events (event_id, ticket_id, status, message) VALUES (?, ?, ?, ?)")
                .params(UUID.randomUUID(), id, status.name(), message).update();
    }
}
