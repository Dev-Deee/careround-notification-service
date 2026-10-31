package com.dayo.careround.notificationservice.data.repositories;

import com.dayo.careround.notificationservice.data.models.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Optional<Alert> findByPublicId(UUID publicId);
    boolean existsBySourceEventId(String sourceEventId);
    Optional<Alert> findBySourceEventId(String sourceEventId);
    List<Alert> findByAcknowledgedAtIsNull();
}