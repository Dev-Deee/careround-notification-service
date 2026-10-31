package com.dayo.careround.notificationservice.controllers;

import com.dayo.careround.notificationservice.data.models.enums.NotificationChannel;
import com.dayo.careround.notificationservice.data.models.enums.NotificationStatus;
import com.dayo.careround.notificationservice.dto.response.AdminNotificationResponse;
import com.dayo.careround.notificationservice.dto.response.PageResponse;
import com.dayo.careround.notificationservice.services.AdminNotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Delivery log for admins (any ADMIN). Recipients are masked; bodies are never stored.
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final AdminNotificationQueryService adminNotificationQueryService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminNotificationResponse>> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(adminNotificationQueryService.list(status, channel, page, size));
    }

    // Shortcut for status=FAILED.
    @GetMapping("/failed")
    public ResponseEntity<PageResponse<AdminNotificationResponse>> failed(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(adminNotificationQueryService.list(NotificationStatus.FAILED, channel, page, size));
    }
}
