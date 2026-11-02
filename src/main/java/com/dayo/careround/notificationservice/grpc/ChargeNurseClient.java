package com.dayo.careround.notificationservice.grpc;

import com.dayo.careround.wardservice.grpc.ActiveChargeNurseRequest;
import com.dayo.careround.wardservice.grpc.ActiveChargeNurseResponse;
import com.dayo.careround.wardservice.grpc.ChargeVerificationServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Who holds charge on a ward right now (ward-service GetActiveChargeNurse). Best effort on listener threads.
@Slf4j
@Service
public class ChargeNurseClient {

    @GrpcClient("ward-service")
    private ChargeVerificationServiceGrpc.ChargeVerificationServiceBlockingStub chargeStub;

    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // Empty when nobody holds charge, or ward-service cannot answer (logged WARN, never thrown).
    public Optional<UUID> activeChargeNurse(UUID wardId) {
        try {
            ActiveChargeNurseResponse reply = chargeStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .getActiveChargeNurse(ActiveChargeNurseRequest.newBuilder().setWardId(wardId.toString()).build());
            return reply.getFound() ? Optional.of(UUID.fromString(reply.getNurseId())) : Optional.empty();
        } catch (StatusRuntimeException e) {
            log.warn("Could not look up the charge nurse of ward {} ({})", wardId, e.getStatus().getCode());
            return Optional.empty();
        }
    }
}
