package com.dayo.careround.notificationservice.dto.event;

import java.util.UUID;

public record PatientReviewCompletedEvent(
        UUID patientId,
        UUID roundId,
        String statusSummary,
        UUID resolvedByDoctorId
) {}