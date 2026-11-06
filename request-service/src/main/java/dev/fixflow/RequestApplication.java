package dev.fixflow;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RequestApplication {
    static final String QUEUE = "fixflow.ticket-events";

    public static void main(String[] args) {
        SpringApplication.run(RequestApplication.class, args);
    }

    @Bean
    Queue ticketEvents() {
        return QueueBuilder.durable(QUEUE).build();
    }
}
