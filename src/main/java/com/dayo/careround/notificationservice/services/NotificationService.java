package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.SentNotification;
import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;
import com.dayo.careround.notificationservice.data.repositories.SentNotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SentNotificationRepository sentNotificationRepository;

    @Transactional
    public void send(String idempotencyKey, NotificationChannel channel, String recipient,
                     String eventType, String body) {

        if (sentNotificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Notification with key {} already sent — skipping duplicate", idempotencyKey);
            return;
        }

        // Real send would happen here (SendGrid/Twilio API call).
        // Stopgap: console-log, same pattern used everywhere else in this project.
        log.info("NOTIFICATION [{} -> {}]: {}", channel, recipient, body);

        SentNotification record = new SentNotification();
        record.setIdempotencyKey(idempotencyKey);
        record.setChannel(channel);
        record.setRecipient(recipient);
        record.setEventType(eventType);
        record.setStatus(NotificationStatus.SENT);
        sentNotificationRepository.save(record);
    }
}