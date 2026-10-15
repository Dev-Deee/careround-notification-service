package com.dayo.careround.notificationservice.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    // Transient failures (gRPC UNAVAILABLE/DEADLINE_EXCEEDED, DB hiccups) are retried 1s, 2s, 4s, 8s,
    // then the record is published to "<topic>-dlt" and the offset moves on. Poison messages
    // (unparseable JSON, missing ids) skip the retries and go straight to the dead-letter topic.
    // Total time per record stays well under max.poll.interval.ms even with gRPC deadlines.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(4);

        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}
