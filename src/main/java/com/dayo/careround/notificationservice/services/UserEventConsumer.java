package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.dto.event.UserRegisteredEvent;
import com.dayo.careround.notificationservice.dto.event.VerificationResentEvent;
import com.dayo.careround.notificationservice.grpc.ActivationTokenClient;
import com.dayo.careround.notificationservice.utils.IdempotencyKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Exceptions propagate to KafkaErrorHandlingConfig (retry, then dead-letter) so a failed
// notification is never committed as if it had been handled.
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ActivationTokenClient activationTokenClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Auth returns an empty link once the Redis token's TTL (1h registration / 30m resend) has passed.
    private static final String ACTIVATION_TOKEN_EXPIRED =
            "activation token expired or missing in auth-service; admin can reissue via POST /api/v1/auth/admin/resend-activation";

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void handleUserRegistered(ConsumerRecord<String, String> record) throws JsonProcessingException {
        UserRegisteredEvent event = objectMapper.readValue(record.value(), UserRegisteredEvent.class);
        String activationUrl = activationTokenClient.getActivationLink(requireUserId(event.userId(), record));

        if (activationUrl.isEmpty()) {
            notificationService.recordFailure(IdempotencyKeys.fromRecord(record), NotificationChannel.EMAIL,
                    event.email(), "user.registered", ACTIVATION_TOKEN_EXPIRED);
            return;
        }

        String body = String.format(
                "Welcome %s %s. Activate your account: %s",
                event.firstName(), event.lastName(), activationUrl
        );

        notificationService.send(
                IdempotencyKeys.fromRecord(record),
                NotificationChannel.EMAIL,
                event.email(),
                "user.registered",
                body
        );
    }

    @KafkaListener(topics = "user.verification_resent", groupId = "notification-service-group")
    public void handleVerificationResent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        VerificationResentEvent event = objectMapper.readValue(record.value(), VerificationResentEvent.class);
        String activationUrl = activationTokenClient.getActivationLink(requireUserId(event.userId(), record));

        if (activationUrl.isEmpty()) {
            notificationService.recordFailure(IdempotencyKeys.fromRecord(record), NotificationChannel.EMAIL,
                    event.email(), "user.verification_resent", ACTIVATION_TOKEN_EXPIRED);
            return;
        }

        String body = String.format("Your activation link has been renewed: %s", activationUrl);

        notificationService.send(
                IdempotencyKeys.fromRecord(record),
                NotificationChannel.EMAIL,
                event.email(),
                "user.verification_resent",
                body
        );
    }

    private UUID requireUserId(UUID userId, ConsumerRecord<String, String> record) {
        if (userId == null) {
            throw new IllegalArgumentException(record.topic() + " event " + IdempotencyKeys.fromRecord(record) + " has no userId");
        }
        return userId;
    }
}
