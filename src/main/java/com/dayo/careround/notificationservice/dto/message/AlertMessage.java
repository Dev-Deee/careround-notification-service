package com.dayo.careround.notificationservice.dto.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on the Redis alerts channel for realtime-service (schemaVersion 1, additive only).
 * Ids only: no names, no clinical detail.
 *
 * @param alertId  Alert.publicId; null for ALERT_RESOLVED
 * @param eventId  Kafka record key of the source event (Alert.sourceEventId for ALERT_RAISED)
 * @param wardId   ward publicId; null when it could not be resolved (realtime routes it to admins)
 */
public record AlertMessage(
        int schemaVersion,
        String kind,
        UUID alertId,
        String eventId,
        String type,
        UUID wardId,
        UUID patientId,
        Instant occurredAt,
        UUID acknowledgedBy
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String ALERT_RAISED = "ALERT_RAISED";
    public static final String ALERT_RESOLVED = "ALERT_RESOLVED";
    public static final String ALERT_ACKNOWLEDGED = "ALERT_ACKNOWLEDGED";
}
