package com.dsva.client;

import com.dsva.Node;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.proto.chat_bully.MessageRequest;
import com.proto.chat_bully.MessageResponse;
import com.proto.chat_bully.NodeGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public record Client(Address myAddress, DSNeighbours myNeighbours, Node node) {
    public void sendClientMessage(int nodeId, String message) {
        int targetNodePort = myNeighbours.getTargetNodePort(nodeId);

        if (targetNodePort == -1) {
            System.out.println("You have probably typed wrong node id. All available nodes: "
                    + myNeighbours.getKnownNodes());
            return;
        }

        ManagedChannel channel = buildManagedChannel(Constants.HOSTNAME, targetNodePort);

        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        MessageRequest request = MessageRequest.newBuilder()
                .setMessage(message)
                .setSenderId(String.valueOf(node.getNodeId()))
                .build();

        MessageResponse messageResponse = stub.sendMessage(request);
        if (messageResponse.getAck()) {
            System.out.printf("Node with: %d, successfully got a message!%n", nodeId);
        } else {
            System.out.println("Node did not respond. Something went wrong.");
            // leader election
        }
        channel.shutdown();
    }

    private ManagedChannel buildManagedChannel(String hostname, int targetNodePort) {
        return ManagedChannelBuilder.forAddress(hostname, targetNodePort)
                .usePlaintext()
                .build();
    }
}
