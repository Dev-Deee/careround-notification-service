package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.patientservice.grpc.PatientCensusServiceGrpc;
import com.dayo.careround.patientservice.grpc.PatientExistsRequest;
import com.dayo.careround.patientservice.grpc.PatientExistsResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Fallback for events that arrive without a wardId (round-service could not reach patient-service).
@Slf4j
@Service
public class PatientWardClient {

    @GrpcClient("patient-service")
    private PatientCensusServiceGrpc.PatientCensusServiceBlockingStub patientStub;

    // Calls run on Kafka listener threads; without a deadline a hung peer blocks the listener indefinitely.
    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // Best effort: empty when the patient is unknown, has no ward, or patient-service cannot answer.
    // The alert is still raised then, flagged wardUnresolved and routed to admins.
    public Optional<UUID> findWardId(UUID patientId) {
        PatientExistsRequest request = PatientExistsRequest.newBuilder()
                .setPatientId(patientId.toString())
                .build();
        try {
            PatientExistsResponse reply = patientStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).patientExists(request);
            if (!reply.getExists() || reply.getWardId().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(reply.getWardId()));
        } catch (StatusRuntimeException e) {
            log.warn("Could not look up ward of patient {} ({})", patientId, e.getStatus().getCode());
            return Optional.empty();
        }
    }
}
