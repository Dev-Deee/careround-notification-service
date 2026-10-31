package com.dayo.careround.notificationservice.data.repositories;

import com.dayo.careround.notificationservice.data.models.SentNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SentNotificationRepository extends JpaRepository<SentNotification, UUID>, JpaSpecificationExecutor<SentNotification> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}