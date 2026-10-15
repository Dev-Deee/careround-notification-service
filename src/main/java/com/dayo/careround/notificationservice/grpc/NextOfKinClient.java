package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.patientservice.grpc.NextOfKinRequest;
import com.dayo.careround.patientservice.grpc.NextOfKinResponse;
import com.dayo.careround.patientservice.grpc.PatientCensusServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class NextOfKinClient {

    @GrpcClient("patient-service")
    private PatientCensusServiceGrpc.PatientCensusServiceBlockingStub patientStub;

    // Calls run on Kafka listener threads; without a deadline a hung peer blocks the listener indefinitely.
    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    public NextOfKinResponse getPrimaryNextOfKin(UUID patientId) {
        NextOfKinRequest request = NextOfKinRequest.newBuilder()
                .setPatientId(patientId.toString())
                .build();
        return patientStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).getPrimaryNextOfKin(request);
    }
}