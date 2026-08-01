package com.dayo.careround.notificationservice.data.models;

import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.models.enums.EscalationState;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Column
    private UUID wardId;

    @Column
    private UUID patientId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column
    private UUID acknowledgedBy;

    @Column
    private Instant acknowledgedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationState escalationState = EscalationState.PENDING;
}