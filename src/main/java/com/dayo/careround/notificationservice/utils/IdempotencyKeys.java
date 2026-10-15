package com.dayo.careround.notificationservice.utils;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class IdempotencyKeys {

    private IdempotencyKeys() {}

    // Producers publish through an outbox whose idempotencyKey is the record key
    // (e.g. "emergency-declared-{episodeId}"), so redelivery, outbox re-publish and
    // offset-reset replays all map to the same key. Keyless records fall back to their
    // log position, which is stable across redelivery and replay of that record.
    public static String fromRecord(ConsumerRecord<String, String> record) {
        if (record.key() != null && !record.key().isBlank()) {
            return record.key();
        }
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }
}
