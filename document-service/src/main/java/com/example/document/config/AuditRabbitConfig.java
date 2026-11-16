package com.example.document.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the durable route consumed by audit-notification-service. */
@Configuration
public class AuditRabbitConfig {

    public static final String AUDIT_QUEUE = "profile_events_queue";
    public static final String AUDIT_EXCHANGE = "audit_exchange";
    public static final String AUDIT_ROUTING_KEY = "audit_routing_key";

    @Bean
    public Queue auditEventsQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    public DirectExchange auditEventsExchange(
            @Value("${audit.rabbitmq.exchange:audit_exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Binding auditEventsBinding(
            Queue auditEventsQueue,
            DirectExchange auditEventsExchange,
            @Value("${audit.rabbitmq.routing-key:audit_routing_key}") String routingKey) {
        return BindingBuilder.bind(auditEventsQueue).to(auditEventsExchange).with(routingKey);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
