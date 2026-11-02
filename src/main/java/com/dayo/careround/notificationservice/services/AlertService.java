package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.data.models.enums.AlertSeverity;
import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.repositories.AlertRepository;
import com.dayo.careround.notificationservice.dto.event.MedicationAdministeredEvent;
import com.dayo.careround.notificationservice.dto.event.MedicationOverdueEvent;
import com.dayo.careround.notificationservice.dto.event.PatientEmergencyDeclaredEvent;
import com.dayo.careround.notificationservice.dto.event.PatientEmergencyResolvedEvent;
import com.dayo.careround.notificationservice.dto.event.TaskOverdueEvent;
import com.dayo.careround.notificationservice.dto.message.AlertMessage;
import com.dayo.careround.notificationservice.grpc.ChargeNurseClient;
import com.dayo.careround.notificationservice.grpc.PatientWardClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    // sourceEventId suffixes for events that raise one alert per recipient: the unique sourceEventId stays the
    // idempotency guard ("<record key>:doctor", "<record key>:charge"), so redelivery creates no second alert.
    public static final String PRESCRIBER = ":doctor";
    public static final String CHARGE_NURSE = ":charge";

    private final AlertRepository alertRepository;
    private final PatientWardClient patientWardClient;
    private final ChargeNurseClient chargeNurseClient;
    private final AlertPublisher alertPublisher;
    private final TransactionTemplate transactionTemplate;

    // What to store and announce for one alert. targetUserId null = a ward alert.
    public record AlertSpec(String sourceEventId, AlertType type, UUID patientId, UUID wardId, UUID targetUserId,
                            AlertSeverity severity, boolean requiresAck, UUID taskPublicId, Instant occurredAt) {}

    /**
     * Saves the emergency Alert and announces ALERT_RAISED after commit. sourceEventId is the Kafka record key,
     * so a redelivered or replayed event finds the existing row and neither creates nor re-publishes anything.
     */
    public void raiseEmergencyAlert(PatientEmergencyDeclaredEvent event, String sourceEventId) {
        if (alertRepository.existsBySourceEventId(sourceEventId)) {
            log.info("Alert for event {} already raised — skipping duplicate", sourceEventId);
            return;
        }
        UUID wardId = resolveWard(event.wardId(), event.patientId(), sourceEventId);
        raise(new AlertSpec(sourceEventId, AlertType.PATIENT_EMERGENCY_DECLARED, event.patientId(), wardId, null,
                AlertSeverity.CRITICAL, true, null, occurredAt(event.occurredAt(), event.declaredAt())));
    }

    // No row is written: the resolve is only announced. Publishing it twice is harmless for clients.
    public void announceEmergencyResolved(PatientEmergencyResolvedEvent event, String sourceEventId) {
        UUID wardId = event.wardId() != null
                ? event.wardId()
                : patientWardClient.findWardId(event.patientId()).orElse(null);
        alertPublisher.publish(new AlertMessage(
                AlertMessage.SCHEMA_VERSION, AlertMessage.ALERT_RESOLVED, null, sourceEventId,
                AlertType.PATIENT_EMERGENCY_DECLARED.name(), wardId, event.patientId(),
                occurredAt(event.occurredAt(), event.resolvedAt()), null, null, AlertSeverity.CRITICAL.name(), null, null));
    }

    /**
     * Two targeted CRITICAL alerts: the prescribing doctor, and the ward's active charge nurse (ward-service). A
     * charge-nurse lookup that fails, or a ward with no charge holder or no known ward, skips that alert with a WARN;
     * the prescriber's alert is always raised.
     */
    public void raiseMedicationOverdue(MedicationOverdueEvent event, String recordKey) {
        Instant at = occurredAt(event.occurredAt(), null);
        UUID wardId = resolveWard(event.wardId(), event.patientId(), recordKey);
        if (event.prescribedBy() == null) {
            log.warn("MEDICATION_OVERDUE {} has no prescribedBy — prescriber alert skipped", recordKey);
        } else {
            raiseOnce(new AlertSpec(recordKey + PRESCRIBER, AlertType.MEDICATION_OVERDUE, event.patientId(), wardId,
                    event.prescribedBy(), AlertSeverity.CRITICAL, true, event.taskPublicId(), at));
        }
        String chargeKey = recordKey + CHARGE_NURSE;
        if (alertRepository.existsBySourceEventId(chargeKey)) {
            log.info("Alert {} already raised — skipping duplicate", chargeKey);
            return;
        }
        if (wardId == null) {
            log.warn("MEDICATION_OVERDUE {}: ward unknown — charge-nurse alert skipped", recordKey);
            return;
        }
        Optional<UUID> chargeNurse = chargeNurseClient.activeChargeNurse(wardId);
        if (chargeNurse.isEmpty()) {
            log.warn("MEDICATION_OVERDUE {}: no active charge nurse found for ward {} — charge-nurse alert skipped", recordKey, wardId);
            return;
        }
        raise(new AlertSpec(chargeKey, AlertType.MEDICATION_OVERDUE, event.patientId(), wardId, chargeNurse.get(),
                AlertSeverity.CRITICAL, true, event.taskPublicId(), at));
    }

    // One HIGH ward alert (targetUserId null); no ward -> wardUnresolved, routed to admins.
    public void raiseTaskOverdue(TaskOverdueEvent event, String recordKey) {
        if (alertRepository.existsBySourceEventId(recordKey)) {
            log.info("Alert for event {} already raised — skipping duplicate", recordKey);
            return;
        }
        UUID wardId = resolveWard(event.wardId(), event.patientId(), recordKey);
        raise(new AlertSpec(recordKey, AlertType.TASK_OVERDUE, event.patientId(), wardId, null,
                AlertSeverity.HIGH, true, event.taskPublicId(), occurredAt(event.occurredAt(), null)));
    }

    // An INFO alert to the prescriber that their order was given; nothing to acknowledge.
    public void raiseMedicationAdministered(MedicationAdministeredEvent event, String recordKey) {
        if (event.prescribedBy() == null) {
            log.warn("MEDICATION_ADMINISTERED {} has no prescribedBy — no alert", recordKey);
            return;
        }
        if (alertRepository.existsBySourceEventId(recordKey)) {
            log.info("Alert for event {} already raised — skipping duplicate", recordKey);
            return;
        }
        UUID wardId = resolveWard(event.wardId(), event.patientId(), recordKey);
        raise(new AlertSpec(recordKey, AlertType.MEDICATION_ADMINISTERED, event.patientId(), wardId, event.prescribedBy(),
                AlertSeverity.INFO, false, event.careTaskPublicId(), occurredAt(event.occurredAt(), null)));
    }

    private void raiseOnce(AlertSpec spec) {
        if (alertRepository.existsBySourceEventId(spec.sourceEventId())) {
            log.info("Alert {} already raised — skipping duplicate", spec.sourceEventId());
            return;
        }
        raise(spec);
    }

    // Stores the alert and announces ALERT_RAISED after commit; a concurrent duplicate (unique sourceEventId) is a no-op.
    private void raise(AlertSpec spec) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Alert alert = new Alert();
                alert.setType(spec.type());
                alert.setSourceEventId(spec.sourceEventId());
                alert.setPatientId(spec.patientId());
                alert.setWardId(spec.wardId());
                // Only a ward alert is undeliverable without a ward; a targeted one still reaches its recipient.
                alert.setWardUnresolved(spec.targetUserId() == null && spec.wardId() == null);
                alert.setTargetUserId(spec.targetUserId());
                alert.setSeverity(spec.severity());
                alert.setRequiresAck(spec.requiresAck());
                alert.setTaskPublicId(spec.taskPublicId());
                alertRepository.saveAndFlush(alert);
                alertPublisher.publishAfterCommit(AlertMessage.of(AlertMessage.ALERT_RAISED, alert, spec.occurredAt()));
            });
        } catch (DataIntegrityViolationException e) {
            if (!alertRepository.existsBySourceEventId(spec.sourceEventId())) {
                throw e;
            }
            log.info("Alert {} raised concurrently — skipping duplicate", spec.sourceEventId());
        }
    }

    private UUID resolveWard(UUID wardId, UUID patientId, String sourceEventId) {
        if (wardId != null) return wardId;
        UUID resolved = patientWardClient.findWardId(patientId).orElse(null);
        if (resolved == null) {
            log.warn("Ward of patient {} unresolved for event {}", patientId, sourceEventId);
        }
        return resolved;
    }

    private static Instant occurredAt(Instant occurredAt, Instant fallback) {
        if (occurredAt != null) return occurredAt;
        return fallback != null ? fallback : Instant.now();
    }
}
