package com.dsva.server;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.proto.chat_bully.*;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;

@Slf4j
@AllArgsConstructor
public class ServerImpl extends NodeGrpc.NodeImplBase {

    private final Node myNode;

    @Override
    public void sendMessage(MessageRequest request, StreamObserver<MessageResponse> responseObserver) {
        if (myNode.isLeader() && request.getReceiverId() != myNode.getNodeId()) {
            try {
                myNode.getClient().distributeMessage(request.getReceiverId(), request.getSenderId(), request.getMessage());
            } catch (NodeNotFoundException e) {
                sendAcknowledgment(responseObserver, MessageResponse.newBuilder()
                        .setAck(false)
                        .build());
                return;
            }
        } else {
            log.info("Received message from Node ID: {} to Node ID {}. Content: '{}'", request.getSenderId(),
                    request.getReceiverId(), request.getMessage());
        }
        sendAcknowledgment(responseObserver, MessageResponse.newBuilder()
                .setAck(true)
                .build());
    }

    @Override
    public void startElection(ElectionRequest request, StreamObserver<ElectionResponse> responseObserver) {
        int candidateId = request.getNodeId();

        ElectionResponse electionResponse = ElectionResponse.newBuilder()
                .setAck(candidateId <= myNode.getNodeId())
                .build();

        responseObserver.onNext(electionResponse);
        responseObserver.onCompleted();
    }

    @Override
    public void announceLeader(LeaderAnnouncement request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        log.info("Got announce leader request! Setting a new leader...");
        int leaderPort = Constants.DEFAULT_PORT + request.getLeaderId();
        Address leaderAddress = new Address(
                Constants.HOSTNAME,
                leaderPort,
                request.getLeaderId()
        );

        myNode.getClient().myNeighbours().setLeaderAddress(leaderAddress);
        log.info("New leader with ID: {}, on port: {}", request.getLeaderId(), leaderPort);
        responseObserver.onCompleted();
    }

    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        int joiningNodeId = request.getPort() - Constants.DEFAULT_PORT;
        log.info("Join request from myNode ID: {} with port: {}", joiningNodeId, request.getPort());

        JoinResponse.Builder joinResponse = JoinResponse.newBuilder();

        if (!isValidJoinRequest(joiningNodeId)) {
            sendAcknowledgment(responseObserver, joinResponse.setAck(false).build());
            return;
        }

        sendAcknowledgment(responseObserver, buildJoinResponse());
        addNewNodeToTopology(request);
    }

    private JoinResponse buildJoinResponse() {
        return JoinResponse.newBuilder()
                .setAck(true)
                .setLeader(getCurrentProtoLeader())
                .setAvailableNodesAddressesList(getCurrentAvailableNodesProtoAddresses())
                .build();
    }

    private void addNewNodeToTopology(JoinRequest request) {
        Address newNode = new Address(request.getHostname(), request.getPort(), request.getNodeId());
        myNode.getClient().myNeighbours().addNewNode(newNode);
    }

    private boolean isValidJoinRequest(int joiningNodeId) {
        if (myNode.getNodeId() == joiningNodeId) {
            log.warn("Node with the same ID as yours trying to join.");
            return false;
        }

        if (myNode.getNodeId() < 1) {
            log.warn("Invalid myNode id: {}", myNode.getNodeId());
            return false;
        }

        if (myNode.getClient().myNeighbours().isNodePresent(joiningNodeId)) {
            log.warn("Node is already in topology! Not adding.");
            return false;
        }
        return true;
    }

    private com.proto.chat_bully.Address getCurrentProtoLeader() {
        return com.proto.chat_bully.Address.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(myNode.getClient().myNeighbours().getLeaderAddress().port())
                .setNodeId(myNode.getClient().myNeighbours().getLeaderAddress().nodeId())
                .build();
    }

    private AvailableNodesAddressesList getCurrentAvailableNodesProtoAddresses() {
        HashSet<Address> currentAddresses = myNode.getClient().myNeighbours().getKnownNodes();
        AvailableNodesAddressesList.Builder availableNodesAddressesList = AvailableNodesAddressesList.newBuilder();

        for (Address nodeAddress : currentAddresses) {
            availableNodesAddressesList.addAddresses(buildProtoAddress(nodeAddress.port(), nodeAddress.nodeId()));
        }

        availableNodesAddressesList.addAddresses(buildProtoAddress(myNode.getClient().myAddress().port(), myNode.getNodeId()));

        return availableNodesAddressesList.build();
    }

    private com.proto.chat_bully.Address buildProtoAddress(int port, int nodeId) {
        return com.proto.chat_bully.Address.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(port)
                .setNodeId(nodeId)
                .build();
    }

    private <T> void sendAcknowledgment(StreamObserver<T> responseObserver, T response) {
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}