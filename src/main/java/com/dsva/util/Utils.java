package com.dsva.util;

import com.dsva.model.Address;
import com.dsva.model.Constants;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {

    private Utils() {
        throw new UnsupportedOperationException("Can not init static class");
    }

    public static int getNodePortFromNodeId(int nodeId) {
        return nodeId + Constants.DEFAULT_PORT;
    }

    public static Address convertProtoModelToModelAddress(com.proto.chat_bully.Address protoAddress) {
        return new Address(protoAddress.getHostname(), protoAddress.getPort(), protoAddress.getNodeId());
    }

    public static <T> void sendAcknowledgment(StreamObserver<T> responseObserver, T response) {
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public static ManagedChannel buildManagedChannel(int targetNodePort) {
        return ManagedChannelBuilder.forAddress(Constants.HOSTNAME, targetNodePort)
                .usePlaintext()
                .build();
    }

    public static void sleep() {
        try {
            Thread.sleep(Constants.MAX_ACCEPTABLE_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted during retry delay", e);
        }
    }
}
