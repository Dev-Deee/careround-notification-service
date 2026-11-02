package com.dayo.careround.notificationservice.utils;

import com.dayo.careround.notificationservice.data.models.enums.AlertSeverity;
import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.dto.response.AlertResponse;

public final class AlertMapper {

    private AlertMapper() {}

    public static AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getPublicId(), alert.getType(), alert.getWardId(), alert.getPatientId(), alert.getCreatedAt(),
                alert.getAcknowledgedBy(), alert.getAcknowledgedAt(), alert.getEscalationState(),
                alert.getTargetUserId(), severityOf(alert), requiresAckOf(alert), alert.getTaskPublicId(),
                alert.getAcknowledgedAsAdmin()
        );
    }

    // Rows from before severity/requiresAck existed are all emergency alerts: CRITICAL, must be acknowledged.
    public static AlertSeverity severityOf(Alert alert) {
        return alert.getSeverity() != null ? alert.getSeverity() : AlertSeverity.CRITICAL;
    }

    public static boolean requiresAckOf(Alert alert) {
        return alert.getRequiresAck() == null || alert.getRequiresAck();
    }
}
