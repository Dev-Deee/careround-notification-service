package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.repositories.AlertRepository;
import com.dayo.careround.notificationservice.dto.event.PatientEmergencyDeclaredEvent;
import com.dayo.careround.notificationservice.dto.event.PatientEmergencyResolvedEvent;
import com.dayo.careround.notificationservice.dto.message.AlertMessage;
import com.dayo.careround.notificationservice.grpc.PatientWardClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final PatientWardClient patientWardClient;
    private final AlertPublisher alertPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Saves the emergency Alert and announces ALERT_RAISED after commit. sourceEventId is the Kafka record key,
     * so a redelivered or replayed event finds the existing row and neither creates nor re-publishes anything.
     */
    public void raiseEmergencyAlert(PatientEmergencyDeclaredEvent event, String sourceEventId) {
        if (alertRepository.existsBySourceEventId(sourceEventId)) {
            log.info("Alert for event {} already raised — skipping duplicate", sourceEventId);
            return;
        }

        UUID wardId = event.wardId() != null
                ? event.wardId()
                : patientWardClient.findWardId(event.patientId()).orElse(null);
        if (wardId == null) {
            log.warn("Ward of patient {} unresolved for event {} — alert goes to admins", event.patientId(), sourceEventId);
        }

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Alert alert = new Alert();
                alert.setType(AlertType.PATIENT_EMERGENCY_DECLARED);
                alert.setSourceEventId(sourceEventId);
                alert.setPatientId(event.patientId());
                alert.setWardId(wardId);
                alert.setWardUnresolved(wardId == null);
                alertRepository.saveAndFlush(alert);

                alertPublisher.publishAfterCommit(new AlertMessage(
                        AlertMessage.SCHEMA_VERSION, AlertMessage.ALERT_RAISED, alert.getPublicId(), sourceEventId,
                        AlertType.PATIENT_EMERGENCY_DECLARED.name(), wardId, event.patientId(),
                        occurredAt(event.occurredAt(), event.declaredAt()), null));
            });
        } catch (DataIntegrityViolationException e) {
            if (!alertRepository.existsBySourceEventId(sourceEventId)) {
                throw e;
            }
            log.info("Alert for event {} raised concurrently — skipping duplicate", sourceEventId);
        }
    }

    // No row is written: the resolve is only announced. Publishing it twice is harmless for clients.
    public void announceEmergencyResolved(PatientEmergencyResolvedEvent event, String sourceEventId) {
        UUID wardId = event.wardId() != null
                ? event.wardId()
                : patientWardClient.findWardId(event.patientId()).orElse(null);

        alertPublisher.publish(new AlertMessage(
                AlertMessage.SCHEMA_VERSION, AlertMessage.ALERT_RESOLVED, null, sourceEventId,
                AlertType.PATIENT_EMERGENCY_DECLARED.name(), wardId, event.patientId(),
                occurredAt(event.occurredAt(), event.resolvedAt()), null));
    }

    private static Instant occurredAt(Instant occurredAt, Instant fallback) {
        if (occurredAt != null) return occurredAt;
        return fallback != null ? fallback : Instant.now();
    }
}
