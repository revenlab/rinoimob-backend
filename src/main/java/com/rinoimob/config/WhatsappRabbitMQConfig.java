package com.rinoimob.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WhatsappRabbitMQConfig {

    public static final String EVOLUTION_EXCHANGE = "evolution";
    public static final String MESSAGES_QUEUE = "rinoimob.evolution.messages";
    public static final String CONNECTION_QUEUE = "rinoimob.evolution.connection";

    @Bean
    public TopicExchange evolutionExchange() {
        return ExchangeBuilder.topicExchange(EVOLUTION_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue messagesQueue() {
        return QueueBuilder.durable(MESSAGES_QUEUE).build();
    }

    @Bean
    public Queue connectionQueue() {
        return QueueBuilder.durable(CONNECTION_QUEUE).build();
    }

    @Bean
    public Binding messagesBinding(Queue messagesQueue, TopicExchange evolutionExchange) {
        return BindingBuilder.bind(messagesQueue).to(evolutionExchange).with("*.MESSAGES_UPSERT");
    }

    @Bean
    public Binding connectionBinding(Queue connectionQueue, TopicExchange evolutionExchange) {
        return BindingBuilder.bind(connectionQueue).to(evolutionExchange).with("*.CONNECTION_UPDATE");
    }
}
