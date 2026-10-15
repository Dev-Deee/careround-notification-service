package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.patientservice.grpc.NextOfKinRequest;
import com.dayo.careround.patientservice.grpc.NextOfKinResponse;
import com.dayo.careround.patientservice.grpc.PatientCensusServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class NextOfKinClient {

    @GrpcClient("patient-service")
    private PatientCensusServiceGrpc.PatientCensusServiceBlockingStub patientStub;

    // Calls run on Kafka listener threads; without a deadline a hung peer blocks the listener indefinitely.
    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // Empty when patient-service does not know the patient (NOT_FOUND). INVALID_ARGUMENT becomes an
    // IllegalArgumentException so the error handler dead-letters it without retrying; other statuses
    // (UNAVAILABLE, DEADLINE_EXCEEDED, ...) propagate and are retried as transient.
    public Optional<NextOfKinResponse> getPrimaryNextOfKin(UUID patientId) {
        NextOfKinRequest request = NextOfKinRequest.newBuilder()
                .setPatientId(patientId.toString())
                .build();
        try {
            return Optional.of(patientStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).getPrimaryNextOfKin(request));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new IllegalArgumentException("patient-service rejected patient id " + patientId + ": "
                        + e.getStatus().getDescription(), e);
            }
            throw e;
        }
    }
}
