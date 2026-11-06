package dev.fixflow;

import org.springframework.amqp.core.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NotificationApplication {
    static final String QUEUE = "fixflow.ticket-events";

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

    @Bean
    Queue ticketEvents() {
        return QueueBuilder.durable(QUEUE).build();
    }
}
