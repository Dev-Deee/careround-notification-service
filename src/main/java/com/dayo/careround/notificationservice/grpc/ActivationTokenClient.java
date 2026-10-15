package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.authservice.grpc.ActivationLinkRequest;
import com.dayo.careround.authservice.grpc.ActivationLinkResponse;
import com.dayo.careround.authservice.grpc.ActivationTokenServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ActivationTokenClient {

    @GrpcClient("auth-service")
    private ActivationTokenServiceGrpc.ActivationTokenServiceBlockingStub activationStub;

    // Calls run on Kafka listener threads; without a deadline a hung peer blocks the listener indefinitely.
    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    public String getActivationLink(UUID userId) {
        ActivationLinkRequest request = ActivationLinkRequest.newBuilder()
                .setUserId(userId.toString())
                .build();
        return activationStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).getActivationLink(request).getActivationUrl();
    }
}