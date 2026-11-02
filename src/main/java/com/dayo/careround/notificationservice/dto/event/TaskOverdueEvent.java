package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

// From care-task-service when a HIGH-priority non-medication task's overdue timer fires.
public record TaskOverdueEvent(
        UUID taskPublicId,
        UUID patientId,
        UUID wardId,
        String type,
        String priority,
        Instant deadline,
        Instant occurredAt
) {}
