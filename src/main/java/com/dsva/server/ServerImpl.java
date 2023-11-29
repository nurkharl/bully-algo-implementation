package com.dsva.server;

import com.dsva.Node;
import com.proto.chat_bully.*;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ServerImpl extends NodeGrpc.NodeImplBase {
    private final Node node;

    @Override
    public void sendMessage(MessageRequest request, StreamObserver<Empty> responseObserver) {
        log.info("New message from Node with ID: {}", request.getSenderId());
        log.info("Message: {}", request.getMessage());
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
        super.announceLeader(request, responseObserver);
    }

    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        super.join(request, responseObserver);
    }
}
