package com.dsva.client;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.service.LeaderElectionService;
import com.dsva.service.MessageService;
import com.dsva.service.ShutdownService;
import com.dsva.service.TopologyService;
import com.dsva.util.Utils;
import com.proto.chat_bully.AvailableNodesAddressesList;
import com.proto.chat_bully.JoinRequest;
import com.proto.chat_bully.JoinResponse;
import com.proto.chat_bully.NodeGrpc;
import io.grpc.ManagedChannel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@Slf4j
public class Client {

    @Getter
    private final Address myAddress;
    @Getter
    private final DSNeighbours myNeighbours;
    private final Node myNode;
    @Setter
    private TopologyService topologyService;
    private final MessageService messageService;
    private final LeaderElectionService leaderElectionService;

    private final ShutdownService shutdownService;

    public Client(Address myAddress, DSNeighbours myNeighbours, Node myNode) {
        this.myAddress = myAddress;
        this.myNeighbours = myNeighbours;
        this.myNode = myNode;
        this.messageService = new MessageService(this.myNeighbours);
        this.leaderElectionService = new LeaderElectionService(this.myNeighbours, this.myNode);
        this.shutdownService = new ShutdownService(this);
    }

    public void sendMessageViaLeader(int receiverNodeId, String message) throws NodeNotFoundException {
        boolean viaLeader = !this.myNode.isLeader();
        boolean messageSent = false;
        int retryCount = 0;

        while (!messageSent && retryCount < Constants.MAX_RETRIES) {
            messageSent = messageService.sendGrpcMessage(receiverNodeId, myNode.getNodeId(), message, viaLeader);
            if (!messageSent) {
                log.warn("Retrying message send. Attempt: {}", retryCount + 1);
                Utils.sleep();
            }
            retryCount++;
        }

        if (!messageSent) {
            Address leaderAddress = myNode.getClient().getMyNeighbours().getLeaderAddress();
            log.error("Failed to send message after {} attempts", Constants.MAX_RETRIES);
            log.info("Checking if leader is alive...");
            if(!topologyService.checkNodeHealthAndHandleFailure(leaderAddress.nodeId())) {
                myNode.getClient().myNeighbours.removeNode(leaderAddress.nodeId());
                initiateElection();
            }

        }
    }

    public void distributeMessage(int receiverNodeId, int senderNodeId, String message) throws NodeNotFoundException {
        log.info("As a leader I distribute a message from {} to {}", senderNodeId, receiverNodeId);
        if (!messageService.sendGrpcMessage(receiverNodeId, senderNodeId, message, false)) {
            topologyService.checkNodeHealthAndHandleFailure(receiverNodeId);
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
                log.info("Your node: {} joined network topology successfully. Topology:\n {}", myNode.getNodeId(), myNeighbours.toString());
                return;
            }
        }

        log.warn("Failed to join network topology after trying all known nodes.");
    }

    public void sendCurrentTopology(int joiningNodeId) {
        if (!myNode.isLeader()) {
            return;
        }

        for (Address address : myNeighbours.getKnownNodes().values()) {
            if (address.nodeId() != joiningNodeId) {
                try {
                    topologyService.updateNodeTopology(address);
                } catch (NodeNotFoundException e) {
                    log.error("Node not found: {}", e.getMessage());
                }
            }
        }
    }

    public void initiateElection() {
        leaderElectionService.initiateElection();
    }


    private boolean joinNetworkTopology(int expectedNodeIdToBeAlive) {
        int targetNodePort = Utils.getNodePortFromNodeId(expectedNodeIdToBeAlive);
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel)
                .withDeadlineAfter(Constants.MAX_ACCEPTABLE_DELAY, TimeUnit.SECONDS);

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
            log.error("Error joining network topology through node {}: {}", expectedNodeIdToBeAlive, e.getMessage());
            return false;
        }
    }

    private void setUpDSNeighbours(com.proto.chat_bully.Address leader, AvailableNodesAddressesList availableNodesAddressesList) {
        log.info("Setting up new neighbours. New leader node ID: {}", leader.getNodeId());
        // TODO check if ID is higher
        myNeighbours.setLeaderAddress(Utils.convertProtoModelToModelAddress(leader));

        ConcurrentHashMap<Integer, Address> knownNodes = new ConcurrentHashMap<>();
        for (com.proto.chat_bully.Address protoAddress : availableNodesAddressesList.getAddressesList()) {
            Address address = Utils.convertProtoModelToModelAddress(protoAddress);
            knownNodes.put(address.nodeId(), address);
        }

        log.info("Updated neighbours: {}", knownNodes);
        myNeighbours.setKnownNodes(knownNodes);
    }

    public void quitTopologyWithNotification(int senderNodeId) {
        try {
            shutdownService.quitTopologyWithNotification(senderNodeId);
        } catch (NodeNotFoundException e) {
            log.error("Can't find a node with id: {}", senderNodeId);
        }
    }

    public void quitTopologyWithoutNotification() {
        shutdownService.quitTopologyWithoutNotification();
    }
}