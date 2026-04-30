package com.rinoimob.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WhatsappRabbitMQConfig {

    // Queues created and managed by Evolution API — we just listen, don't declare
    public static final String MESSAGES_QUEUE = "evolution.messages.upsert";
    public static final String CONNECTION_QUEUE = "evolution.connection.update";

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
