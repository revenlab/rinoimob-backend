package com.rinoimob.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "events.exchange";
    public static final String EVENTS_QUEUE = "events.queue";
    public static final String EVENTS_ROUTING_KEY = "event.*";

    @Bean
    public Queue eventsQueue() {
        return new Queue(EVENTS_QUEUE, true, false, false);
    }

    @Bean
    public Exchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Binding eventsBinding(Queue eventsQueue, Exchange eventsExchange) {
        return BindingBuilder.bind(eventsQueue)
                .to((TopicExchange) eventsExchange)
                .with(EVENTS_ROUTING_KEY);
    }
}
