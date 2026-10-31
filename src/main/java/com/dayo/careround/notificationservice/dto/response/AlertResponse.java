package com.dayo.careround.notificationservice.dto.response;

import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.models.enums.EscalationState;

import java.time.Instant;
import java.util.UUID;

// Ids and state only: no names, no clinical detail.
public record AlertResponse(
        UUID publicId,
        AlertType type,
        UUID wardId,
        UUID patientId,
        Instant createdAt,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        EscalationState escalationState
) {}
