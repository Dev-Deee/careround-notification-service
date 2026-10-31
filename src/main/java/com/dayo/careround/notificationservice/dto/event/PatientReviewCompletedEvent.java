package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record PatientReviewCompletedEvent(
        UUID patientId,
        UUID wardId,
        UUID roundId,
        String statusSummary,
        UUID resolvedByDoctorId,
        Instant occurredAt
) {}
