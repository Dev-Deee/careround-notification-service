package com.dayo.careround.notificationservice.services;

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
                dispatch(channel, recipient, body);
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
        log.warn("Notification {} [{} -> {}] not sent: {}", idempotencyKey, channel, recipient, reason);
        try {
            sentNotificationRepository.saveAndFlush(
                    newRecord(idempotencyKey, channel, recipient, eventType, NotificationStatus.FAILED));
        } catch (DataIntegrityViolationException e) {
            if (!sentNotificationRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw e;
            }
            log.info("Notification with key {} already recorded — skipping duplicate", idempotencyKey);
        }
    }

    private void dispatch(NotificationChannel channel, String recipient, String body) {
        // Real send would happen here (SendGrid/Twilio API call).
        // Stopgap: console-log, same pattern used everywhere else in this project.
        log.info("NOTIFICATION [{} -> {}]: {}", channel, recipient, body);
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
