package com.dayo.careround.notificationservice.controllers;

import com.dayo.careround.notificationservice.dto.response.AlertResponse;
import com.dayo.careround.notificationservice.security.CareRoundPrincipal;
import com.dayo.careround.notificationservice.services.AlertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final AlertQueryService alertQueryService;

    // Ward-scoped access is checked in the service (own assignedWardId, or ADMIN).
    @GetMapping("/wards/{wardId}/alerts")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('NURSE') or hasRole('ADMIN')")
    public ResponseEntity<List<AlertResponse>> getWardAlerts(
            @PathVariable UUID wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(defaultValue = "false") boolean unacknowledgedOnly,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(alertQueryService.getWardAlerts(wardId, since, unacknowledgedOnly, principal));
    }

    @GetMapping("/alerts/unresolved-ward")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertResponse>> getUnresolvedWardAlerts() {
        return ResponseEntity.ok(alertQueryService.getUnresolvedWardAlerts());
    }

    @PostMapping("/alerts/{publicId}/acknowledge")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('NURSE') or hasRole('ADMIN')")
    public ResponseEntity<AlertResponse> acknowledge(
            @PathVariable UUID publicId,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(alertQueryService.acknowledge(publicId, principal));
    }
}
