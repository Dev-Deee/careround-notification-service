package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.authservice.grpc.ActivationLinkRequest;
import com.dayo.careround.authservice.grpc.ActivationLinkResponse;
import com.dayo.careround.authservice.grpc.ActivationTokenServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ActivationTokenClient {

    @GrpcClient("auth-service")
    private ActivationTokenServiceGrpc.ActivationTokenServiceBlockingStub activationStub;

    public String getActivationLink(UUID userId) {
        ActivationLinkRequest request = ActivationLinkRequest.newBuilder()
                .setUserId(userId.toString())
                .build();
        return activationStub.getActivationLink(request).getActivationUrl();
    }
}