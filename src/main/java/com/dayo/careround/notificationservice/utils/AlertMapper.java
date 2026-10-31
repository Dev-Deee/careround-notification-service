package com.dayo.careround.notificationservice.utils;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.dto.response.AlertResponse;

public final class AlertMapper {

    private AlertMapper() {}

    public static AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getPublicId(), alert.getType(), alert.getWardId(), alert.getPatientId(), alert.getCreatedAt(),
                alert.getAcknowledgedBy(), alert.getAcknowledgedAt(), alert.getEscalationState()
        );
    }
}
