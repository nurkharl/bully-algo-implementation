package com.dsva.service;

import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.MessageRequest;
import com.proto.chat_bully.MessageResponse;
import com.proto.chat_bully.NodeGrpc;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class MessageService {

    private final DSNeighbours myNeighbours;

    public boolean sendGrpcMessage(int receiverNodeId, int senderNodeId, String message, boolean viaLeader) throws NodeNotFoundException {
        int targetNodeId = viaLeader ? myNeighbours.getLeaderAddress().nodeId() : receiverNodeId;
        int targetNodePort = myNeighbours.getTargetNodePort(targetNodeId);
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);

        try {
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            MessageRequest request = RequestBuilder.buildMessageRequest(message, senderNodeId, receiverNodeId);

            MessageResponse messageResponse = stub.sendMessage(request);

            if (messageResponse.getAck()) {
                log.info("Node with id: {}, successfully received a message", receiverNodeId);
                return true;
            } else {
                log.error("Message delivery failed or false ack received.");
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending gRPC message: {}", e.getMessage());
            return false;
        } finally {
            channel.shutdown();

            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for the channel to terminate", e);
            }
        }
    }
}
