package com.dayo.careround.notificationservice.data.repositories;

import com.dayo.careround.notificationservice.data.models.SentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SentNotificationRepository extends JpaRepository<SentNotification, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}