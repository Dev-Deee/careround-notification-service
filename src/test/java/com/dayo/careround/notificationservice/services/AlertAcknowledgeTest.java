package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.data.models.enums.AlertSeverity;
import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.repositories.AlertRepository;
import com.dayo.careround.notificationservice.dto.message.AlertMessage;
import com.dayo.careround.notificationservice.security.CareRoundPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertAcknowledgeTest {

    private final AlertRepository alerts = mock(AlertRepository.class);
    private final AlertPublisher publisher = mock(AlertPublisher.class);
    private final AlertQueryService service = new AlertQueryService(alerts, publisher);
    private final UUID ward = UUID.randomUUID(), doctor = UUID.randomUUID();

    private Alert targeted(Boolean requiresAck) {
        Alert a = new Alert();
        a.setType(AlertType.MEDICATION_OVERDUE);
        a.setSourceEventId("medication-overdue-1:doctor");
        a.setWardId(ward);
        a.setPatientId(UUID.randomUUID());
        a.setTargetUserId(doctor);
        a.setSeverity(AlertSeverity.CRITICAL);
        a.setRequiresAck(requiresAck);
        when(alerts.findByPublicIdForUpdate(a.getPublicId())).thenReturn(Optional.of(a));
        return a;
    }

    private static CareRoundPrincipal who(UUID id, String role, UUID ward) {
        return new CareRoundPrincipal(id, role, null, ward);
    }

    @Test
    void targetMayAcknowledge() {
        Alert a = targeted(true);
        service.acknowledge(a.getPublicId(), who(doctor, "DOCTOR", ward));
        assertThat(a.getAcknowledgedBy()).isEqualTo(doctor);
        assertThat(a.getAcknowledgedAsAdmin()).isNull();
        ArgumentCaptor<AlertMessage> m = ArgumentCaptor.forClass(AlertMessage.class);
        verify(publisher).publishAfterCommit(m.capture());
        assertThat(m.getValue().kind()).isEqualTo("ALERT_ACKNOWLEDGED");
        assertThat(m.getValue().targetUserId()).isEqualTo(doctor); // the acknowledgement goes to the same personal topic
    }

    @Test
    void someoneElseOnTheSameWardIsDenied() {
        Alert a = targeted(true);
        assertThatThrownBy(() -> service.acknowledge(a.getPublicId(), who(UUID.randomUUID(), "NURSE", ward)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(a.getAcknowledgedAt()).isNull();
        verify(publisher, never()).publishAfterCommit(any());
    }

    @Test
    void adminMayAcknowledgeAndIsAudited() {
        Alert a = targeted(true);
        UUID admin = UUID.randomUUID();
        service.acknowledge(a.getPublicId(), who(admin, "ADMIN", null));
        assertThat(a.getAcknowledgedBy()).isEqualTo(admin);
        assertThat(a.getAcknowledgedAsAdmin()).isTrue();
    }

    @Test
    void infoAlertCanBeDismissedIdempotently() {
        Alert a = targeted(false);
        var first = service.acknowledge(a.getPublicId(), who(doctor, "DOCTOR", ward));
        var again = service.acknowledge(a.getPublicId(), who(doctor, "DOCTOR", ward));
        assertThat(again.acknowledgedAt()).isEqualTo(first.acknowledgedAt());
        assertThat(first.requiresAck()).isFalse();
        verify(publisher, times(1)).publishAfterCommit(any());
    }

    @Test
    void wardAlertRulesAreUnchanged() {
        Alert a = targeted(true);
        a.setTargetUserId(null);
        service.acknowledge(a.getPublicId(), who(UUID.randomUUID(), "NURSE", ward));
        assertThat(a.getAcknowledgedAt()).isNotNull();
        Alert b = targeted(true);
        b.setTargetUserId(null);
        assertThatThrownBy(() -> service.acknowledge(b.getPublicId(), who(UUID.randomUUID(), "NURSE", UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legacyRowsReadAsCriticalAndRequiringAck() {
        Alert a = targeted(null);
        a.setSeverity(null);
        a.setTargetUserId(null);
        var r = service.acknowledge(a.getPublicId(), who(UUID.randomUUID(), "ADMIN", null));
        assertThat(r.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(r.requiresAck()).isTrue();
    }
}
