package com.dayo.careround.notificationservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record PatientEmergencyDeclaredEvent(
        UUID patientId,
        UUID declaredBy,
        Instant declaredAt
) {}