package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.SentNotification;
import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;
import com.dayo.careround.notificationservice.data.repositories.SentNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    void recordFailureStoresTheReason() {
        SentNotificationRepository repository = mock(SentNotificationRepository.class);
        when(repository.existsByIdempotencyKey("k1")).thenReturn(false);
        NotificationService service = new NotificationService(repository, mock(TransactionTemplate.class));

        service.recordFailure("k1", NotificationChannel.EMAIL, "a@b.test", "user.registered", "token expired");

        ArgumentCaptor<SentNotification> saved = ArgumentCaptor.forClass(SentNotification.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(NotificationStatus.FAILED, saved.getValue().getStatus());
        assertEquals("token expired", saved.getValue().getFailureReason());
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void deliveryLogMasksTheRecipientAndHidesTheBodyWhenPayloadLoggingIsOff(org.springframework.boot.test.system.CapturedOutput output) {
        SentNotificationRepository repository = mock(SentNotificationRepository.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        org.mockito.Mockito.doAnswer(inv -> { inv.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0).accept(null); return null; })
                .when(tx).executeWithoutResult(org.mockito.ArgumentMatchers.any());
        NotificationService service = new NotificationService(repository, tx);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "logDeliveryPayload", false);

        service.send("k2", NotificationChannel.SMS, "08061230001", "PATIENT_EMERGENCY_DECLARED", "secret body with link?token=abc");
        service.send("k3", NotificationChannel.EMAIL, "ada@host.test", "user.registered", "Activate: http://x/?token=def");

        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("[SMS -> 0806***0001] key=k2").contains("[EMAIL -> a***@host.test] key=k3")
                .doesNotContain("08061230001").doesNotContain("ada@host.test").doesNotContain("token=");
    }
}
