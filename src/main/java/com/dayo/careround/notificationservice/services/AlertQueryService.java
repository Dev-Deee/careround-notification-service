package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.data.models.enums.EscalationState;
import com.dayo.careround.notificationservice.data.repositories.AlertRepository;
import com.dayo.careround.notificationservice.dto.message.AlertMessage;
import com.dayo.careround.notificationservice.dto.response.AlertResponse;
import com.dayo.careround.notificationservice.exception.AlertNotFoundException;
import com.dayo.careround.notificationservice.security.CareRoundPrincipal;
import com.dayo.careround.notificationservice.utils.AlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Replay and acknowledgement for the alerts realtime-service pushes. Replay is how a tablet recovers alerts
 * it missed while disconnected (Redis pub/sub keeps nothing).
 */
@Service
@RequiredArgsConstructor
public class AlertQueryService {

    public static final int MAX_RESULTS = 200;
    public static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    private final AlertRepository alertRepository;
    private final AlertPublisher alertPublisher;

    @Transactional(readOnly = true)
    public List<AlertResponse> getWardAlerts(UUID wardId, Instant since, boolean unacknowledgedOnly, CareRoundPrincipal caller) {
        requireWardAccess(wardId, caller);
        Instant from = since != null ? since : Instant.now().minus(DEFAULT_WINDOW);
        PageRequest cap = PageRequest.of(0, MAX_RESULTS);
        List<Alert> alerts = unacknowledgedOnly
                ? alertRepository.findByWardIdAndCreatedAtGreaterThanEqualAndAcknowledgedAtIsNullOrderByCreatedAtDesc(wardId, from, cap)
                : alertRepository.findByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(wardId, from, cap);
        return alerts.stream().map(AlertMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getUnresolvedWardAlerts() {
        return alertRepository.findByWardUnresolvedTrueAndAcknowledgedAtIsNullOrderByCreatedAtDesc(PageRequest.of(0, MAX_RESULTS))
                .stream().map(AlertMapper::toResponse).toList();
    }

    /**
     * Idempotent: the first acknowledgement is recorded and announced (ALERT_ACKNOWLEDGED, after commit);
     * a repeat returns the original acknowledgement and publishes nothing.
     */
    @Transactional
    public AlertResponse acknowledge(UUID alertPublicId, CareRoundPrincipal caller) {
        Alert alert = alertRepository.findByPublicIdForUpdate(alertPublicId)
                .orElseThrow(() -> new AlertNotFoundException("No alert with that id"));
        // An alert without a ward (wardUnresolved) can only be acknowledged by an admin.
        requireWardAccess(alert.getWardId(), caller);

        if (alert.getAcknowledgedAt() != null) {
            return AlertMapper.toResponse(alert);
        }

        alert.setAcknowledgedBy(caller.userId());
        alert.setAcknowledgedAt(Instant.now());
        alert.setEscalationState(EscalationState.ACKNOWLEDGE);
        alertRepository.save(alert);

        alertPublisher.publishAfterCommit(new AlertMessage(
                AlertMessage.SCHEMA_VERSION, AlertMessage.ALERT_ACKNOWLEDGED, alert.getPublicId(), alert.getSourceEventId(),
                alert.getType().name(), alert.getWardId(), alert.getPatientId(), alert.getAcknowledgedAt(), alert.getAcknowledgedBy()));

        return AlertMapper.toResponse(alert);
    }

    // ADMIN: any ward. DOCTOR/NURSE: only the ward in their token. Everyone else: denied.
    private static void requireWardAccess(UUID wardId, CareRoundPrincipal caller) {
        if ("ADMIN".equals(caller.role())) {
            return;
        }
        boolean clinical = "DOCTOR".equals(caller.role()) || "NURSE".equals(caller.role());
        if (!clinical || wardId == null || !wardId.equals(caller.assignedWardId())) {
            throw new AccessDeniedException("Not your ward");
        }
    }
}
