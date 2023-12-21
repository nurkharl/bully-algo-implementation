package com.dsva.service;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.pattern.builder.ResponseBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class TopologyService {
    private final Node myNode;
    private final DSNeighbours myNeighbours;
    @Setter private LeaderElectionService leaderElectionService;

    public void joinTopology(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        if (!isValidJoinRequest(request)) {
            sendNegativeAcknowledgment(responseObserver);
            return;
        }
        processJoinRequest(request, responseObserver);
    }

    public void updateTopology(AvailableNodesAddressesList addressesToAdd, StreamObserver<UpdateTopologyResponse> responseObserver) {
        ConcurrentHashMap<Integer, Address> knownNodes = new ConcurrentHashMap<>();
        addressesToAdd.getAddressesList().forEach(protoAddress -> {
            Address address = Utils.convertProtoModelToModelAddress(protoAddress);
            knownNodes.put(address.nodeId(), address);
        });
        this.myNeighbours.setKnownNodes(knownNodes);
        log.info("Node's topology updated:\n {}", knownNodes);
        Utils.sendAcknowledgment(responseObserver, ResponseBuilder.buildUpdateTopologyResponse(true));
    }

    public void addNewNodeToTopology(JoinRequest request) {
        Address newNodeAddress = new Address(request.getHostname(), request.getPort(), request.getNodeId());
        myNeighbours.addNewNode(newNodeAddress);
    }

    public void updateNodeTopology(Address address) throws NodeNotFoundException {
        Address targetNodeAddress = myNeighbours.getTargetNodeAddress(address.nodeId());
        ManagedChannel channel = Utils.buildManagedChannel(targetNodeAddress.port(), targetNodeAddress.hostname());

        try {
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(Constants.MAX_ACCEPTABLE_DELAY, TimeUnit.MILLISECONDS);
            UpdateTopologyRequest request = RequestBuilder.buildUpdateTopologyRequest(
                    myNeighbours.getCurrentAvailableNodesProtoAddresses(this.myNode.getClient().getMyAddress().port(),
                            this.myNode.getNodeId(), address.nodeId(), this.myNode.getClient().getMyAddress().hostname()));
            UpdateTopologyResponse response = stub.updateTopology(request);
            log.info("Sending updateTopology req to " + address);
            if (!response.getAck()) {
                log.error("Network topology delivery failed or false ack received for node {}", address.nodeId());
            }
        } catch (Exception e) {
            log.error("Error sending gRPC updateTopology to node {}: {}", address.nodeId(), e.getMessage());
        } finally {
            channel.shutdown();
        }
    }

    public void removeNodeFromTopology(QuitTopologyRequest request, StreamObserver<QuitTopologyResponse> responseObserver) {
        QuitTopologyResponse response;
        if (!isValidQuitRequest(request)) {
            log.error("Node received invalid quit topology request: {}", request);
            response = ResponseBuilder.buildQuitTopologyResponse(false);
            Utils.sendAcknowledgment(responseObserver, response);
        } else {
            myNode.getClient().getMyNeighbours().removeNode(request.getSenderNodeId());
            if (myNode.isLeader()) {
                updateAllNodesTopologyKnowledge();
            }
            response = ResponseBuilder.buildQuitTopologyResponse(true);
            Utils.sendAcknowledgment(responseObserver, response);
            if (request.getSenderNodeId() == myNode.getClient().getLeaderAddress().nodeId()) {
                log.info("Leader logged out from the topology! Currently there is no leader. Start leader election");
                leaderElectionService.initiateElection();
            }
        }
    }

    public boolean checkNodeHealthAndHandleFailure(int targetNodeId) {
        AtomicBoolean isNodeAlive = new AtomicBoolean(false);

        for (int i = 0; i < Constants.MAX_RETRIES; i++) {
            checkIfNodeIsAlive(targetNodeId, isAlive -> {
                if (Boolean.FALSE.equals(isAlive)) {
                    log.info("Node with id: {} does not respond", targetNodeId);
                    isNodeAlive.set(false);
                } else {
                    isNodeAlive.set(true);
                }
            });
            Utils.sleep();
        }

        if (!isNodeAlive.get()) {
            log.info("Node didn't respond for {} times. Deleting node from the topology", Constants.MAX_RETRIES);
            myNeighbours.removeNode(targetNodeId);
            updateAllNodesTopologyKnowledge();
            return false;
        } else {
            log.info("Node with id: {} is alive", targetNodeId);
            return true;
        }
    }

    public void updateAllNodesTopologyKnowledge() {
        log.info("Sending updateTopology to remaining nodes...");
        for (Address address : myNeighbours.getKnownNodes().values()) {
            try {
                this.updateNodeTopology(address);
            } catch (NodeNotFoundException e) {
                log.error("Node not found: {}", e.getMessage());
            }
        }
        if (myNeighbours.getKnownNodes().size() == 0) {
            log.info("No node is available to update its' topology knowledge.");
        }
    }

    private void checkIfNodeIsAlive(int targetNodeId, MessagePassingQueue.Consumer<Boolean> onResult) {
        int targetNodePort;
        String targetNodeHostname;
        try {
            Address targetNodeAddress = myNeighbours.getTargetNodeAddress(targetNodeId);
            targetNodePort = targetNodeAddress.port();
            targetNodeHostname = targetNodeAddress.hostname();
        } catch (NodeNotFoundException e) {
            log.error(e.getMessage());
            return;
        }
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort, targetNodeHostname);
        NodeGrpc.NodeStub stub = NodeGrpc.newStub(channel)
                .withDeadlineAfter(Constants.MAX_ACCEPTABLE_DELAY, TimeUnit.SECONDS);
        AliveRequest request = RequestBuilder.buildAliveRequest(myNode.getNodeId());

        stub.isNodeAlive(request, new StreamObserver<>() {
            @Override
            public void onNext(AliveResponse aliveResponse) {
                if (aliveResponse.getAck()) {
                    log.info("Node with id: {} is still alive", targetNodeId);
                    onResult.accept(true);
                } else {
                    log.error("Health checking failed or false ack received.");
                    onResult.accept(false);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error checking if node {} is alive: {}", targetNodeId, t.toString());
                onResult.accept(false);
            }

            @Override
            public void onCompleted() {
                channel.shutdownNow();
            }
        });

        channel.shutdown();
    }


    private boolean isValidJoinRequest(JoinRequest request) {
        return myNode.getNodeId() != request.getNodeId() && request.getPort() > Constants.DEFAULT_PORT;
    }

    private boolean isValidQuitRequest(QuitTopologyRequest request) {
        int nodeIdToRemove = request.getSenderNodeId();
        return myNode.getClient().getMyNeighbours().isNodePresent(nodeIdToRemove);
    }

    private void processJoinRequest(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        this.addNewNodeToTopology(request);
        myNode.getClient().sendCurrentTopology(request.getNodeId());
        sendPositiveAcknowledgment(responseObserver, request.getNodeId());
    }

    private void sendPositiveAcknowledgment(StreamObserver<JoinResponse> responseObserver, int senderId) {
        JoinResponse joinResponse = ResponseBuilder.buildJoinResponse(true,
                myNeighbours.getCurrentProtoLeader(),
                myNeighbours.getCurrentAvailableNodesProtoAddresses(myNode.getClient().getMyAddress().port(), myNode.getNodeId(), senderId, myNode.getClient().getMyAddress().hostname())
        );
        Utils.sendAcknowledgment(responseObserver, joinResponse);
    }

    private void sendNegativeAcknowledgment(StreamObserver<JoinResponse> responseObserver) {
        JoinResponse joinResponse = ResponseBuilder.buildJoinResponse(false);
        Utils.sendAcknowledgment(responseObserver, joinResponse);
    }
}