package com.dayo.careround.notificationservice.data.models;

import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "sent_notifications")
public class SentNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String recipient; // phone or email — kept minimal, no clinical detail per PHI rules

    @Column(nullable = false)
    private String eventType; // what triggered it, e.g. "PATIENT_EMERGENCY_DECLARED"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    // Why a FAILED record was not sent (no recipient, gRPC lookup failed, ...). Null for SENT.
    @Column
    private String failureReason;
}