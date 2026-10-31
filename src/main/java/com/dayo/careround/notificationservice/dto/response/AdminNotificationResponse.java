package com.dayo.careround.notificationservice.dto.response;

import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

// Delivery record for the admin console. The recipient is masked and the message body is never stored or returned.
public record AdminNotificationResponse(
        UUID id,
        String idempotencyKey,
        String eventType,
        NotificationChannel channel,
        NotificationStatus status,
        String recipientMasked,
        String failureReason, // null for SENT, and for failures recorded before the column existed
        Instant sentAt
) {}
