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
}
