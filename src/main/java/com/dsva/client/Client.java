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

    public void sendMessage(int receiverNodeId, String message) throws NodeNotFoundException {
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
            Address leaderAddress = myNeighbours.getLeaderAddress();
            log.error("Failed to send message after {} attempts", Constants.MAX_RETRIES);
            int nodeIdToCheck = leaderAddress.nodeId();
            if (leaderAddress.equals(myAddress)) {
                nodeIdToCheck = receiverNodeId;
                log.info("Checking if node with id: {} alive", nodeIdToCheck);
            } else {
                log.info("Checking if leader is alive...");
            }
            if(!topologyService.checkNodeHealthAndHandleFailure(nodeIdToCheck)) {
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
        log.info("Trying to connect to someone in topology...");
        for (int nodeId = Constants.EXPECTED_ENTRY_POINT_NODE_ID; nodeId > 0; nodeId--) {
            if (nodeId == myNode.getNodeId()) {
                continue;
            }

            if (joinNetworkTopology(nodeId)) {
                log.info("Your node: {} joined network topology successfully. Topology:\n {}", myNode.getNodeId(), myNeighbours.toString());
                if (nodeId < myNode.getNodeId()) {
                    log.info("However my ID is higher, so I will initiate a leader election process.");
                    initiateElection();
                }
                return;
            }
        }
        log.info("I tried to connect to all possible nodes. No one responded. Become a leader.");
        leaderElectionService.becomeLeader();
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

    public void quitTopologyWithNotification(int senderNodeId) {
        shutdownService.quitTopologyWithNotification(senderNodeId);
    }

    public void quitTopologyWithoutNotification() {
        shutdownService.quitTopologyWithoutNotification();
    }

    public Address getLeaderAddress() {
        return myNeighbours.getLeaderAddress();
    }

    public void setTopologyService(TopologyService topologyService) {
        this.topologyService = topologyService;
        this.topologyService.setLeaderElectionService(leaderElectionService);
    }

    private boolean joinNetworkTopology(int expectedNodeIdToBeAlive) {
        int targetNodePort = Utils.getNodePortFromNodeId(expectedNodeIdToBeAlive);
        String expectedHostname = Constants.HOSTNAME;
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort, expectedHostname);
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel)
                .withDeadlineAfter(Constants.MAX_ACCEPTABLE_DELAY, TimeUnit.MILLISECONDS);

        JoinRequest request = RequestBuilder.buildJoinRequest(this.myAddress.port(), myNode.getNodeId(), Constants.HOSTNAME);

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
            log.debug("Cannot connect to node {}: {}", expectedNodeIdToBeAlive, e.getMessage());
            return false;
        } finally {
            channel.shutdown();
            Utils.awaitChannelTermination(channel);
        }
    }

    private void setUpDSNeighbours(com.proto.chat_bully.Address leader, AvailableNodesAddressesList availableNodesAddressesList) {
        log.info("Setting up new neighbours. New leader node ID: {}", leader.getNodeId());
        myNeighbours.setLeaderAddress(Utils.convertProtoModelToModelAddress(leader));

        ConcurrentHashMap<Integer, Address> knownNodes = new ConcurrentHashMap<>();
        for (com.proto.chat_bully.Address protoAddress : availableNodesAddressesList.getAddressesList()) {
            Address address = Utils.convertProtoModelToModelAddress(protoAddress);
            knownNodes.put(address.nodeId(), address);
        }

        log.info("Updated neighbours: {}", knownNodes);
        myNeighbours.setKnownNodes(knownNodes);
    }
}