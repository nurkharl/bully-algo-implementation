package com.dsva.service;

import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.MessageRequest;
import com.proto.chat_bully.MessageResponse;
import com.proto.chat_bully.NodeGrpc;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MessageService {

    private final DSNeighbours myNeighbours;

    public boolean sendGrpcMessage(int receiverNodeId, int senderNodeId, String message, boolean viaLeader) throws NodeNotFoundException {
        int targetNodeId = viaLeader ? myNeighbours.getLeaderAddress().nodeId() : receiverNodeId;
        Address targetNodeAddress = myNeighbours.getTargetNodeAddress(targetNodeId);
        ManagedChannel channel = Utils.buildManagedChannel(targetNodeAddress.port(), targetNodeAddress.hostname());

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
            Utils.awaitChannelTermination(channel);
        }
    }
}
