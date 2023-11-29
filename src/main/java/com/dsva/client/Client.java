package com.dsva.client;

import com.dsva.Node;
import com.dsva.model.Address;
import com.dsva.model.DSNeighbours;
import com.proto.chat_bully.MessageRequest;
import com.proto.chat_bully.NodeGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@AllArgsConstructor
public class Client {
    private final Address myAddress;
    private final DSNeighbours myNeighbours;
    private final Node node;

    public void sendClientMessage(int nodeId, String message) {

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", myAddress.port())
                .usePlaintext()
                .build();

        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        MessageRequest request = MessageRequest.newBuilder()
                .setMessage(message)
                .setSenderId(String.valueOf(node.getNodeId()))
                .build();

        stub.sendMessage(request);
        channel.shutdown();
    }

    public void join() {

    }
}
