package com.dsva.server;

import com.dsva.Node;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.proto.chat_bully.*;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ServerImpl extends NodeGrpc.NodeImplBase {

    private final Node node;

    @Override
    public void sendMessage(MessageRequest request, StreamObserver<MessageResponse> responseObserver) {
        log.info("New message from Node with ID: {}", request.getSenderId());
        log.info("Message: {}", request.getMessage());
        responseObserver.onNext(MessageResponse.newBuilder()
                .setAck(true)
                .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void startElection(ElectionRequest request, StreamObserver<ElectionResponse> responseObserver) {
        int candidateId = request.getNodeId();

        ElectionResponse electionResponse = ElectionResponse.newBuilder()
                .setAck(candidateId <= node.getNodeId())
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

        node.getClient().myNeighbours().setLeaderAddress(leaderAddress);
        log.info("New leader with ID: {}, on port: {}", request.getLeaderId(), leaderPort);
        responseObserver.onCompleted();
    }

    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        int joiningNodeId = Integer.parseInt(request.getPort()) - Constants.DEFAULT_PORT;
        log.info("Join request from node ID: {} with port: {}", joiningNodeId, request.getPort());
        log.info("Trying to build a topology...");
    }
}