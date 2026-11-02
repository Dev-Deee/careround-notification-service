package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

// From care-task-service when a medication order's overdue timer fires. wardId may be null.
public record MedicationOverdueEvent(
        UUID taskPublicId,
        UUID patientId,
        UUID wardId,
        UUID prescribedBy,
        String priority,
        Instant deadline,
        Instant occurredAt
) {}
