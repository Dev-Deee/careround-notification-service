package com.dayo.careround.notificationservice.dto.event;

import java.util.UUID;

public record VerificationResentEvent(
        UUID userId,
        String email
) {}