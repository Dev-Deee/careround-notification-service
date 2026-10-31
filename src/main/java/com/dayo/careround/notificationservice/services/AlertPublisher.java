package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.dto.message.AlertMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes alert messages to Redis for realtime-service. Pub/sub is fire-and-forget: a failed publish is
 * logged and swallowed, never failing the Kafka listener or the SMS path. The Alert row stays, and clients
 * recover it from the replay endpoint.
 */
@Slf4j
@Service
public class AlertPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectWriter writer;
    private final String channel;

    public AlertPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                          @Value("${app.alerts.redis-channel:careround.alerts}") String channel) {
        this.redisTemplate = redisTemplate;
        // ISO-8601 instants on the wire, without changing the shared mapper.
        this.writer = objectMapper.writer().without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.channel = channel;
    }

    // Inside a transaction, publishes only once it has committed (nothing is announced for a rolled-back row).
    public void publishAfterCommit(AlertMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(message);
                }
            });
        } else {
            publish(message);
        }
    }

    public void publish(AlertMessage message) {
        try {
            Long receivers = redisTemplate.convertAndSend(channel, writer.writeValueAsString(message));
            log.info("Published {} eventId={} wardId={} to {} ({} subscriber(s))",
                    message.kind(), message.eventId(), message.wardId(), channel, receivers);
        } catch (Exception e) {
            log.error("Failed to publish {} eventId={} to Redis channel {}; clients recover it via replay: {}",
                    message.kind(), message.eventId(), channel, e.toString(), e);
        }
    }
}
