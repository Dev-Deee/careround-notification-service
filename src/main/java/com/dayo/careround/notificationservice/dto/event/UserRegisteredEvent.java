package com.dayo.careround.notificationservice.dto.event;

import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        UUID publicId,
        String email,
        String role,
        String firstName,
        String lastName,
        String grade,
        String specialization,
        String licenseNumber
) {}