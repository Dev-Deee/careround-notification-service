package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.dto.event.PatientEmergencyDeclaredEvent;
import com.dayo.careround.notificationservice.dto.event.PatientReviewCompletedEvent;
import com.dayo.careround.notificationservice.grpc.NextOfKinClient;
import com.dayo.careround.notificationservice.utils.IdempotencyKeys;
import com.dayo.careround.patientservice.grpc.NextOfKinResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// Exceptions propagate to KafkaErrorHandlingConfig (retry, then dead-letter) so a failed
// notification is never committed as if it had been handled.
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalEventConsumer {

    private final NextOfKinClient nextOfKinClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "PATIENT_REVIEW_COMPLETED", groupId = "notification-service-group")
    public void handleReviewCompleted(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PatientReviewCompletedEvent event = objectMapper.readValue(record.value(), PatientReviewCompletedEvent.class);
        notifyNextOfKin(event.patientId(), IdempotencyKeys.fromRecord(record), "PATIENT_REVIEW_COMPLETED",
                "There is an update regarding your family member. Please contact the ward.");
    }

    @KafkaListener(topics = "PATIENT_EMERGENCY_DECLARED", groupId = "notification-service-group")
    public void handleEmergencyDeclared(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PatientEmergencyDeclaredEvent event = objectMapper.readValue(record.value(), PatientEmergencyDeclaredEvent.class);
        notifyNextOfKin(event.patientId(), IdempotencyKeys.fromRecord(record), "PATIENT_EMERGENCY_DECLARED",
                "Please contact the ward immediately regarding your family member.");
    }

    private void notifyNextOfKin(UUID patientId, String idempotencyKey, String eventType, String messageBody) {
        if (patientId == null) {
            throw new IllegalArgumentException(eventType + " event " + idempotencyKey + " has no patientId");
        }

        Optional<NextOfKinResponse> lookup = nextOfKinClient.getPrimaryNextOfKin(patientId);
        if (lookup.isEmpty()) {
            log.warn("Patient {} is unknown to patient-service — cannot send {} notification", patientId, eventType);
            return;
        }
        NextOfKinResponse kin = lookup.get();

        if (!kin.getFound()) {
            log.warn("No next-of-kin found for patient {} — cannot send {} notification", patientId, eventType);
            return;
        }

        boolean hasPhone = !kin.getPhone().isBlank();
        String recipient = hasPhone ? kin.getPhone() : kin.getEmail();
        NotificationChannel channel = hasPhone ? NotificationChannel.SMS : NotificationChannel.EMAIL;

        notificationService.send(
                idempotencyKey,
                channel,
                recipient,
                eventType,
                messageBody
        );
    }
}
