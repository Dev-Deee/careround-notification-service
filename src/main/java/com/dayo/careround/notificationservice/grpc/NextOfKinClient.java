package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.patientservice.grpc.NextOfKinRequest;
import com.dayo.careround.patientservice.grpc.NextOfKinResponse;
import com.dayo.careround.patientservice.grpc.PatientCensusServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NextOfKinClient {

    @GrpcClient("patient-service")
    private PatientCensusServiceGrpc.PatientCensusServiceBlockingStub patientStub;

    public NextOfKinResponse getPrimaryNextOfKin(UUID patientId) {
        NextOfKinRequest request = NextOfKinRequest.newBuilder()
                .setPatientId(patientId.toString())
                .build();
        return patientStub.getPrimaryNextOfKin(request);
    }
}