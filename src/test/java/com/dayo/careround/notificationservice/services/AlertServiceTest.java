package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.Alert;
import com.dayo.careround.notificationservice.data.models.enums.AlertSeverity;
import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.repositories.AlertRepository;
import com.dayo.careround.notificationservice.dto.event.MedicationAdministeredEvent;
import com.dayo.careround.notificationservice.dto.event.MedicationOverdueEvent;
import com.dayo.careround.notificationservice.dto.event.TaskOverdueEvent;
import com.dayo.careround.notificationservice.dto.message.AlertMessage;
import com.dayo.careround.notificationservice.grpc.ChargeNurseClient;
import com.dayo.careround.notificationservice.grpc.PatientWardClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Recipient resolution and idempotency for the Phase 10a alerts.
class AlertServiceTest {

    private final AlertRepository alerts = mock(AlertRepository.class);
    private final PatientWardClient wards = mock(PatientWardClient.class);
    private final ChargeNurseClient charge = mock(ChargeNurseClient.class);
    private final AlertPublisher publisher = mock(AlertPublisher.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final AlertService service = new AlertService(alerts, wards, charge, publisher, tx);
    private final Set<String> stored = new HashSet<>();

    private final UUID patient = UUID.randomUUID(), ward = UUID.randomUUID(), doctor = UUID.randomUUID(),
            chargeNurse = UUID.randomUUID(), task = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        doAnswer(inv -> { ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null); return null; })
                .when(tx).executeWithoutResult(any());
        when(alerts.existsBySourceEventId(any())).thenAnswer(inv -> stored.contains(inv.<String>getArgument(0)));
        when(alerts.saveAndFlush(any())).thenAnswer(inv -> { stored.add(inv.<Alert>getArgument(0).getSourceEventId()); return inv.getArgument(0); });
    }

    private List<Alert> saved() {
        ArgumentCaptor<Alert> c = ArgumentCaptor.forClass(Alert.class);
        verify(alerts, atLeast(0)).saveAndFlush(c.capture());
        return c.getAllValues();
    }

    private MedicationOverdueEvent overdue(UUID wardId) {
        return new MedicationOverdueEvent(task, patient, wardId, doctor, "HIGH", Instant.now(), Instant.now());
    }

    @Test
    void medicationOverdueTargetsPrescriberAndChargeNurse() {
        when(charge.activeChargeNurse(ward)).thenReturn(Optional.of(chargeNurse));
        service.raiseMedicationOverdue(overdue(ward), "medication-overdue-1");

        List<Alert> s = saved();
        assertThat(s).extracting(Alert::getSourceEventId).containsExactly("medication-overdue-1:doctor", "medication-overdue-1:charge");
        assertThat(s).extracting(Alert::getTargetUserId).containsExactly(doctor, chargeNurse);
        assertThat(s).allSatisfy(a -> {
            assertThat(a.getType()).isEqualTo(AlertType.MEDICATION_OVERDUE);
            assertThat(a.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
            assertThat(a.getRequiresAck()).isTrue();
            assertThat(a.getTaskPublicId()).isEqualTo(task);
            assertThat(a.getWardId()).isEqualTo(ward);
        });
        ArgumentCaptor<AlertMessage> m = ArgumentCaptor.forClass(AlertMessage.class);
        verify(publisher, times(2)).publishAfterCommit(m.capture());
        assertThat(m.getAllValues()).extracting(AlertMessage::targetUserId).containsExactly(doctor, chargeNurse);
        assertThat(m.getAllValues()).extracting(AlertMessage::severity).containsOnly("CRITICAL");
    }

    @Test
    void redeliveryCreatesNoSecondAlertForEitherRecipient() {
        when(charge.activeChargeNurse(ward)).thenReturn(Optional.of(chargeNurse));
        service.raiseMedicationOverdue(overdue(ward), "medication-overdue-2");
        service.raiseMedicationOverdue(overdue(ward), "medication-overdue-2");
        assertThat(saved()).hasSize(2);
        verify(charge, times(1)).activeChargeNurse(ward); // the second delivery does not even look the charge nurse up
        verify(publisher, times(2)).publishAfterCommit(any());
    }

    @Test
    void noChargeHolderStillAlertsThePrescriber() {
        when(charge.activeChargeNurse(ward)).thenReturn(Optional.empty()); // nobody, or ward-service down
        service.raiseMedicationOverdue(overdue(ward), "medication-overdue-3");
        assertThat(saved()).extracting(Alert::getTargetUserId).containsExactly(doctor);
    }

    @Test
    void unknownWardStillAlertsThePrescriberAndSkipsTheChargeLookup() {
        when(wards.findWardId(patient)).thenReturn(Optional.empty());
        service.raiseMedicationOverdue(overdue(null), "medication-overdue-4");
        List<Alert> s = saved();
        assertThat(s).extracting(Alert::getTargetUserId).containsExactly(doctor);
        assertThat(s.get(0).isWardUnresolved()).isFalse(); // a targeted alert is still delivered
        verify(charge, never()).activeChargeNurse(any());
    }

    @Test
    void missingWardIsResolvedThroughPatientService() {
        when(wards.findWardId(patient)).thenReturn(Optional.of(ward));
        when(charge.activeChargeNurse(ward)).thenReturn(Optional.of(chargeNurse));
        service.raiseMedicationOverdue(overdue(null), "medication-overdue-5");
        assertThat(saved()).extracting(Alert::getWardId).containsOnly(ward);
    }

    @Test
    void taskOverdueIsOneHighWardAlert() {
        service.raiseTaskOverdue(new TaskOverdueEvent(task, patient, ward, "Obs", "HIGH", Instant.now(), Instant.now()), "task-overdue-1");
        service.raiseTaskOverdue(new TaskOverdueEvent(task, patient, ward, "Obs", "HIGH", Instant.now(), Instant.now()), "task-overdue-1");
        List<Alert> s = saved();
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getTargetUserId()).isNull();
        assertThat(s.get(0).getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(s.get(0).getRequiresAck()).isTrue();
        assertThat(s.get(0).isWardUnresolved()).isFalse();
    }

    @Test
    void taskOverdueWithoutAWardIsWardUnresolved() {
        when(wards.findWardId(patient)).thenReturn(Optional.empty());
        service.raiseTaskOverdue(new TaskOverdueEvent(task, patient, null, "Obs", "HIGH", Instant.now(), Instant.now()), "task-overdue-2");
        assertThat(saved().get(0).isWardUnresolved()).isTrue();
    }

    @Test
    void medicationAdministeredIsAnInfoAlertToThePrescriber() {
        MedicationAdministeredEvent e = new MedicationAdministeredEvent(patient, UUID.randomUUID(), task, doctor, chargeNurse, ward, Instant.now());
        service.raiseMedicationAdministered(e, "medication-administered-1");
        service.raiseMedicationAdministered(e, "medication-administered-1");
        List<Alert> s = saved();
        assertThat(s).hasSize(1);
        assertThat(s.get(0).getTargetUserId()).isEqualTo(doctor);
        assertThat(s.get(0).getSeverity()).isEqualTo(AlertSeverity.INFO);
        assertThat(s.get(0).getRequiresAck()).isFalse();
        assertThat(s.get(0).getType()).isEqualTo(AlertType.MEDICATION_ADMINISTERED);
    }
}
