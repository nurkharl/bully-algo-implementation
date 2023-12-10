package com.dsva.client;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.service.TopologyService;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;


@Slf4j
public class Client {

    @Getter private final Address myAddress;
    @Getter private final DSNeighbours myNeighbours;
    private final Node myNode;
    @Setter private TopologyService topologyService;

    public Client(Address myAddress, DSNeighbours myNeighbours, Node myNode) {
        this.myAddress = myAddress;
        this.myNeighbours = myNeighbours;
        this.myNode = myNode;
    }

    public void sendMessageViaLeader(int receiverNodeId, String message) throws NodeNotFoundException {
        boolean viaLeader = !this.myNode.isLeader();
        boolean messageSent = false;
        int retryCount = 0;

        while (!messageSent && retryCount < Constants.MAX_RETRIES) {
            messageSent = sendGrpcMessage(receiverNodeId, myNode.getNodeId(), message, viaLeader);
            if (!messageSent) {
                log.warn("Retrying message send. Attempt: {}", retryCount + 1);
                try {
                    Thread.sleep(Constants.RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrupted during retry delay", e);
                }
            }
            retryCount++;
        }

        if (!messageSent) {
            log.error("Failed to send message after {} attempts", Constants.MAX_RETRIES);
        }
    }


    public void distributeMessage(int receiverNodeId, int senderNodeId, String message) throws NodeNotFoundException {
        if (sendGrpcMessage(receiverNodeId, senderNodeId, message, false)) {
            // TODO
        }
    }

    public void joinNetworkTopology() {
        if (myNode.getNodeId() == Constants.EXPECTED_ENTRY_POINT_NODE_ID) {
            log.info("I am the leader. No need to join.");
            return;
        }

        for (int nodeId = Constants.EXPECTED_ENTRY_POINT_NODE_ID; nodeId > 0; nodeId--) {
            if (nodeId == myNode.getNodeId()) {
                continue;
            }

            if (joinNetworkTopology(nodeId)) {
                log.info("Joined network topology successfully. Node: {}, Topology: {}", myNode.getNodeId(), myNeighbours.toString());
                return;
            }
        }

        log.warn("Failed to join network topology after trying all known nodes.");
    }

    public void sendCurrentTopology(int joiningNodeId) {
        if (!myNode.isLeader()) {
            return;
        }

        for (Address address : myNeighbours.getKnownNodes()) {
            if (address.nodeId() != joiningNodeId) {
                try {
                    topologyService.updateNodeTopology(address);
                } catch (NodeNotFoundException e) {
                    log.error("Node not found: {}", e.getMessage());
                }
            }
        }
    }


    private boolean sendGrpcMessage(int receiverNodeId, int senderNodeId, String message, boolean viaLeader) throws NodeNotFoundException {
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


    private boolean joinNetworkTopology(int expectedNodeIdToBeAlive) {
        int targetNodePort = Utils.getNodePortFromNodeId(expectedNodeIdToBeAlive);
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        JoinRequest request = RequestBuilder.buildJoinRequest(this.myAddress.port(), myNode.getNodeId());

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
            log.error("Error joining network topology through myNode {}: {}", myNode.getNodeId(), e.getMessage());
            return false;
        }
    }

    private void setUpDSNeighbours(com.proto.chat_bully.Address leader, AvailableNodesAddressesList availableNodesAddressesList) {
        log.info("Setting up new neighbours. New leader myNode ID: {}", leader.getNodeId());
        // TODO check if ID is higher
        myNeighbours.setLeaderAddress(Utils.convertProtoModelToModelAddress(leader));

        HashSet<Address> knownNodes = new HashSet<>();
        for (com.proto.chat_bully.Address protoAddress : availableNodesAddressesList.getAddressesList()) {
            knownNodes.add(Utils.convertProtoModelToModelAddress(protoAddress));
        }

        log.info("Updated neighbours: {}", knownNodes);
        myNeighbours.setKnownNodes(knownNodes);
    }

}