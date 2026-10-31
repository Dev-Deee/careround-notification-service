package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

// wardId is the ward captured at declare time, so it is null whenever the declared event's was.
public record PatientEmergencyResolvedEvent(
        UUID patientId,
        UUID wardId,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant occurredAt
) {}
