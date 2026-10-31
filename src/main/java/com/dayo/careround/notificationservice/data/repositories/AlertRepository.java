package com.dayo.careround.notificationservice.data.repositories;

import com.dayo.careround.notificationservice.data.models.Alert;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Optional<Alert> findByPublicId(UUID publicId);
    boolean existsBySourceEventId(String sourceEventId);
    Optional<Alert> findBySourceEventId(String sourceEventId);
    List<Alert> findByAcknowledgedAtIsNull();

    // Row lock so two simultaneous acknowledgements cannot both "win" and both publish.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alert a where a.publicId = :publicId")
    Optional<Alert> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    List<Alert> findByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(UUID wardId, Instant since, Pageable page);

    List<Alert> findByWardIdAndCreatedAtGreaterThanEqualAndAcknowledgedAtIsNullOrderByCreatedAtDesc(UUID wardId, Instant since, Pageable page);

    List<Alert> findByWardUnresolvedTrueAndAcknowledgedAtIsNullOrderByCreatedAtDesc(Pageable page);
}
