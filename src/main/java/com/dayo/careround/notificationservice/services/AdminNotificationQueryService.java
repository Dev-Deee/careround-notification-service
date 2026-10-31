package com.dayo.careround.notificationservice.services;

import com.dayo.careround.notificationservice.data.models.SentNotification;
import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;
import com.dayo.careround.notificationservice.data.repositories.SentNotificationRepository;
import com.dayo.careround.notificationservice.dto.response.AdminNotificationResponse;
import com.dayo.careround.notificationservice.dto.response.PageResponse;
import com.dayo.careround.notificationservice.utils.RecipientMasker;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNotificationQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final SentNotificationRepository sentNotificationRepository;

    // Newest first. Recipients masked; no message bodies (they are not stored).
    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> list(NotificationStatus status, NotificationChannel channel,
                                                        Integer page, Integer size) {
        Specification<SentNotification> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (channel != null) p.add(cb.equal(root.get("channel"), channel));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE),
                Sort.by(Sort.Order.desc("sentAt"), Sort.Order.asc("id")));
        return PageResponse.of(sentNotificationRepository.findAll(spec, pageable), n -> new AdminNotificationResponse(
                n.getId(), n.getIdempotencyKey(), n.getEventType(), n.getChannel(), n.getStatus(),
                RecipientMasker.mask(n.getRecipient()), n.getFailureReason(), n.getSentAt()));
    }
}
