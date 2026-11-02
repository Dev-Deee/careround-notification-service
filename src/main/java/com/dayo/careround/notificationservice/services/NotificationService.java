package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.utils.RecipientMasker;
import org.springframework.beans.factory.annotation.Value;
import com.dayo.careround.notificationservice.data.models.SentNotification;
import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;
import com.dayo.careround.notificationservice.data.repositories.SentNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SentNotificationRepository sentNotificationRepository;
    private final TransactionTemplate transactionTemplate;

    // Dev default true (developers copy activation links from the console); false in production.
    @Value("${app.notification.log-delivery-payload:true}")
    private boolean logDeliveryPayload = true;

    public void send(String idempotencyKey, NotificationChannel channel, String recipient,
                     String eventType, String body) {

        if (sentNotificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Notification with key {} already sent — skipping duplicate", idempotencyKey);
            return;
        }

        if (recipient == null || recipient.isBlank()) {
            recordFailure(idempotencyKey, channel, "", eventType, "no recipient address");
            return;
        }

        // Claim the key before sending. The insert holds the unique index on idempotency_key until
        // commit, so a concurrent send of the same key blocks and then fails instead of sending twice.
        // If dispatch throws, the claim rolls back and the record can be retried.
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                sentNotificationRepository.saveAndFlush(newRecord(idempotencyKey, channel, recipient, eventType, NotificationStatus.SENT));
                dispatch(idempotencyKey, channel, recipient, body);
            });
        } catch (DataIntegrityViolationException e) {
            if (!sentNotificationRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw e;
            }
            log.info("Notification with key {} claimed concurrently — skipping duplicate", idempotencyKey);
        }
    }

    // Records a notification that could not be sent so it is visible to admins (and not silently dropped).
    public void recordFailure(String idempotencyKey, NotificationChannel channel, String recipient,
                              String eventType, String reason) {
        if (sentNotificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Notification with key {} already recorded — skipping duplicate", idempotencyKey);
            return;
        }
        log.warn("Notification {} [{} -> {}] not sent: {}", idempotencyKey, channel, RecipientMasker.mask(recipient), reason);
        try {
            SentNotification failed = newRecord(idempotencyKey, channel, recipient, eventType, NotificationStatus.FAILED);
            failed.setFailureReason(reason);
            sentNotificationRepository.saveAndFlush(failed);
        } catch (DataIntegrityViolationException e) {
            if (!sentNotificationRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw e;
            }
            log.info("Notification with key {} already recorded — skipping duplicate", idempotencyKey);
        }
    }

    private void dispatch(String idempotencyKey, NotificationChannel channel, String recipient, String body) {
        // Real send would happen here (SendGrid/Twilio API call).
        // Stopgap: console-log, same pattern used everywhere else in this project.
        // Recipients are always masked. The body (it can hold an activation link) only with log-delivery-payload.
        if (logDeliveryPayload) {
            log.info("NOTIFICATION [{} -> {}] key={}: {}", channel, RecipientMasker.mask(recipient), idempotencyKey, body);
        } else {
            log.info("NOTIFICATION [{} -> {}] key={}", channel, RecipientMasker.mask(recipient), idempotencyKey);
        }
    }

    private SentNotification newRecord(String idempotencyKey, NotificationChannel channel, String recipient,
                                       String eventType, NotificationStatus status) {
        SentNotification record = new SentNotification();
        record.setIdempotencyKey(idempotencyKey);
        record.setChannel(channel);
        record.setRecipient(recipient);
        record.setEventType(eventType);
        record.setStatus(status);
        return record;
    }
}
