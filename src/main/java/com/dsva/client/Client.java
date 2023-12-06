package com.dsva.client;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;


@Slf4j
public record Client(Address myAddress, DSNeighbours myNeighbours, Node node) {
    public void sendClientMessage(int nodeId, String message) {
        int targetNodePort;

        try {
            targetNodePort = myNeighbours.getTargetNodePort(nodeId);
        } catch (NodeNotFoundException e) {
            System.out.println("You have probably typed wrong node id. All available nodes: "
                    + myNeighbours.getKnownNodes());
            return;
        }

        ManagedChannel channel = buildManagedChannel(targetNodePort);
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        MessageRequest request = MessageRequest.newBuilder()
                .setMessage(message)
                .setSenderId(String.valueOf(node.getNodeId()))
                .build();

        MessageResponse messageResponse = stub.sendMessage(request);
        if (messageResponse.getAck()) {
            System.out.printf("Node with: %d, successfully got a message!%n", nodeId);
        } else {
            System.out.println("Receiver node did not respond. Something went wrong.");
        }
        channel.shutdown();
    }

    public void joinNetworkTopology() {
        int expectedNodeIdToBeAlive = Constants.EXPECTED_ENTRY_POINT_NODE_ID;

        while (expectedNodeIdToBeAlive > 0 && expectedNodeIdToBeAlive != node.getNodeId()) {

            if (joinNetworkTopology(expectedNodeIdToBeAlive)) {
                log.info("Joined network topology successfully. Node: {}, Topology: {}", node.getNodeId(),
                        myNeighbours.toString());
                return;
            }
            expectedNodeIdToBeAlive--;
        }
        log.warn("Failed to join network topology after trying all known nodes.");
    }

    private boolean joinNetworkTopology(int expectedNodeIdToBeAlive) {
        int targetNodePort = Utils.getNodePortFromNodeId(expectedNodeIdToBeAlive);
        ManagedChannel channel = buildManagedChannel(targetNodePort);
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        JoinRequest request = buildJoinRequest();
        JoinResponse response;

        try {
            response = stub.join(request);
            if (response.getAck()) {
                setUpDSNeighbours(response.getLeader(), response.getAvailableNodesAddressesList());
                return true;
            } else {
                log.info("Node {} responded with false ack", targetNodePort);
                return false;
            }
        } catch (Exception e) {
            log.error("Error joining network topology through node {}: {}", node.getNodeId(), e.getMessage());
            return false;
        }
    }

    private JoinRequest buildJoinRequest() {
        return JoinRequest.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(this.myAddress.port())
                .setNodeId(node.getNodeId())
                .build();
    }

    private void setUpDSNeighbours(com.proto.chat_bully.Address leader, AvailableNodesAddressesList availableNodesAddressesList) {
        log.info("Setting up new neighbours. New leader node ID: {}", leader.getNodeId());
        // TODO check if ID is higher
        myNeighbours.setLeaderAddress(convertProtoModelToModelAddress(leader));

        HashSet<Address> knownNodes = new HashSet<>();
        for (com.proto.chat_bully.Address protoAddress : availableNodesAddressesList.getAddressesList()) {
            knownNodes.add(convertProtoModelToModelAddress(protoAddress));
        }

        log.info("Updated neighbours: {}", knownNodes);
        myNeighbours.setKnownNodes(knownNodes);
    }

    private Address convertProtoModelToModelAddress(com.proto.chat_bully.Address protoAddress) {
        return new Address(protoAddress.getHostname(), protoAddress.getPort(), protoAddress.getNodeId());
    }

    private ManagedChannel buildManagedChannel(int targetNodePort) {
        return ManagedChannelBuilder.forAddress(Constants.HOSTNAME, targetNodePort)
                .usePlaintext()
                .build();
    }
}