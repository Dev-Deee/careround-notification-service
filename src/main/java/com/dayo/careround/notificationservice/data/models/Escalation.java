package com.dayo.careround.notificationservice.data.models;

import com.dayo.careround.notificationservice.data.models.enums.EscalationStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "escalations")
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID raisedBy;

    @Column(nullable = false)
    private String targetType; // e.g. "Alert", "CareTask"

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationStatus status = EscalationStatus.OPEN;

    @Column
    private UUID resolvedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}