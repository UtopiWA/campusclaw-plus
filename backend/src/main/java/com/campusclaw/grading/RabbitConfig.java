package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 持久化直接交换机；重试和死信由发件箱显式路由，不依赖队列 TTL。 */
@Configuration
public class RabbitConfig {
    @Bean
    DirectExchange gradingExchange(GradingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue gradingTasksQueue(GradingProperties properties) {
        return new Queue(properties.taskQueue(), true);
    }

    @Bean
    Queue gradingDeadLetterQueue(GradingProperties properties) {
        return new Queue(properties.deadLetterQueue(), true);
    }

    @Bean
    Binding gradingDispatchBinding(@Qualifier("gradingTasksQueue") Queue queue,
                                   DirectExchange gradingExchange, GradingProperties properties) {
        return BindingBuilder.bind(queue).to(gradingExchange).with(properties.dispatchRoutingKey());
    }

    @Bean
    Binding gradingRetryBinding(@Qualifier("gradingTasksQueue") Queue queue,
                                DirectExchange gradingExchange, GradingProperties properties) {
        return BindingBuilder.bind(queue).to(gradingExchange).with(properties.retryRoutingKey());
    }

    @Bean
    Binding gradingDeadLetterBinding(@Qualifier("gradingDeadLetterQueue") Queue queue,
                                     DirectExchange gradingExchange, GradingProperties properties) {
        return BindingBuilder.bind(queue).to(gradingExchange).with(properties.deadLetterRoutingKey());
    }
}
