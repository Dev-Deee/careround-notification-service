package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

// From care-task-service when a medication task is completed. careTaskId is the deprecated internal id.
public record MedicationAdministeredEvent(
        UUID patientId,
        UUID careTaskId,
        UUID careTaskPublicId,
        UUID prescribedBy,
        UUID administeredBy,
        UUID wardId,
        Instant occurredAt
) {}
