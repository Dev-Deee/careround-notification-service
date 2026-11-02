package com.dayo.careround.notificationservice.dto.message;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.utils.AlertMapper;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on the Redis alerts channel for realtime-service (schemaVersion 1, additive only).
 * Ids only: no names, no clinical detail.
 *
 * @param alertId  Alert.publicId; null for ALERT_RESOLVED
 * @param eventId  Kafka record key of the source event (Alert.sourceEventId for ALERT_RAISED)
 * @param wardId   ward publicId; null when it could not be resolved (realtime routes it to admins)
 * @param targetUserId  set for a targeted alert: realtime pushes it only to /topic/users/{targetUserId}
 * @param severity      CRITICAL | HIGH | INFO
 * @param requiresAck   whether the alert must be acknowledged
 * @param taskPublicId  the care task the alert is about, if any
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
        UUID acknowledgedBy,
        UUID targetUserId,
        String severity,
        Boolean requiresAck,
        UUID taskPublicId
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String ALERT_RAISED = "ALERT_RAISED";
    public static final String ALERT_RESOLVED = "ALERT_RESOLVED";
    public static final String ALERT_ACKNOWLEDGED = "ALERT_ACKNOWLEDGED";

    // The message for a stored alert (raised or acknowledged), carrying its routing fields.
    public static AlertMessage of(String kind, Alert alert, Instant occurredAt) {
        return new AlertMessage(SCHEMA_VERSION, kind, alert.getPublicId(), alert.getSourceEventId(), alert.getType().name(),
                alert.getWardId(), alert.getPatientId(), occurredAt, alert.getAcknowledgedBy(), alert.getTargetUserId(),
                AlertMapper.severityOf(alert).name(), AlertMapper.requiresAckOf(alert), alert.getTaskPublicId());
    }
}
