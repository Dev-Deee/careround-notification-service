package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record PatientReviewCompletedEvent(
        UUID patientId,
        UUID wardId,
        UUID roundId, // deprecated: the round's internal id; use roundPublicId
        String statusSummary,
        UUID resolvedByDoctorId,
        Instant occurredAt,
        UUID roundPublicId
) {}
