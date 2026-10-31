package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

// wardId is null when round-service could not reach patient-service at declare time.
public record PatientEmergencyDeclaredEvent(
        UUID patientId,
        UUID wardId,
        UUID declaredBy,
        Instant declaredAt,
        Instant occurredAt
) {}
