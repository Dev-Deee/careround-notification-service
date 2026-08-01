package com.dayo.careround.notificationservice.data.repositories;

import com.dayo.careround.notificationservice.data.models.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByEventTypeAndLanguage(String eventType, String language);
}