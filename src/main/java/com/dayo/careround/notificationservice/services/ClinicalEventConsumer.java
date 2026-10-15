package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.dto.event.PatientEmergencyDeclaredEvent;
import com.dayo.careround.notificationservice.dto.event.PatientReviewCompletedEvent;
import com.dayo.careround.notificationservice.grpc.NextOfKinClient;
import com.dayo.careround.notificationservice.utils.IdempotencyKeys;
import com.dayo.careround.patientservice.grpc.NextOfKinResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalEventConsumer {

    private final NextOfKinClient nextOfKinClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "PATIENT_REVIEW_COMPLETED", groupId = "notification-service-group")
    public void handleReviewCompleted(ConsumerRecord<String, String> record) {
        try {
            PatientReviewCompletedEvent event = objectMapper.readValue(record.value(), PatientReviewCompletedEvent.class);
            notifyNextOfKin(event.patientId(), IdempotencyKeys.fromRecord(record), "PATIENT_REVIEW_COMPLETED",
                    "There is an update regarding your family member. Please contact the ward.");
        } catch (Exception e) {
            log.error("Failed to process PATIENT_REVIEW_COMPLETED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "PATIENT_EMERGENCY_DECLARED", groupId = "notification-service-group")
    public void handleEmergencyDeclared(ConsumerRecord<String, String> record) {
        try {
            PatientEmergencyDeclaredEvent event = objectMapper.readValue(record.value(), PatientEmergencyDeclaredEvent.class);
            notifyNextOfKin(event.patientId(), IdempotencyKeys.fromRecord(record), "PATIENT_EMERGENCY_DECLARED",
                    "Please contact the ward immediately regarding your family member.");
        } catch (Exception e) {
            log.error("Failed to process PATIENT_EMERGENCY_DECLARED event: {}", e.getMessage(), e);
        }
    }

    private void notifyNextOfKin(java.util.UUID patientId, String idempotencyKey, String eventType, String messageBody) {
        NextOfKinResponse kin = nextOfKinClient.getPrimaryNextOfKin(patientId);

        if (!kin.getFound()) {
            log.warn("No next-of-kin found for patient {} — cannot send {} notification", patientId, eventType);
            return;
        }

        String recipient = !kin.getPhone().isEmpty() ? kin.getPhone() : kin.getEmail();
        NotificationChannel channel = !kin.getPhone().isEmpty() ? NotificationChannel.SMS : NotificationChannel.EMAIL;

        notificationService.send(
                idempotencyKey,
                channel,
                recipient,
                eventType,
                messageBody
        );
    }
}
