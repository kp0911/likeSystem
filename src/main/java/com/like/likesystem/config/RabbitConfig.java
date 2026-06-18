package com.like.likesystem.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange likeExchange() {
        return new DirectExchange("like.exchange");
    }

    @Bean
    public Queue likeAggregateQueue() {
        return new Queue("like.aggregate.queue");
    }

    @Bean
    public Binding likeAggregateBinding(Queue likeAggregateQueue, DirectExchange likeExchange) {
        return BindingBuilder.bind(likeAggregateQueue).to(likeExchange).with("like.aggregate.routing.key");
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
