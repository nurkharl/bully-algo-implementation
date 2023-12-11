package com.dsva.server;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.pattern.builder.ResponseBuilder;
import com.dsva.service.TopologyService;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ServerImpl extends NodeGrpc.NodeImplBase {

    private final Node myNode;
    private final TopologyService topologyService;

    @Override
    public void sendMessage(MessageRequest request, StreamObserver<MessageResponse> responseObserver) {
        if (myNode.isLeader() && request.getReceiverId() != myNode.getNodeId()) {
            try {
                myNode.getClient().distributeMessage(request.getReceiverId(), request.getSenderId(), request.getMessage());
            } catch (NodeNotFoundException e) {
                Utils.sendAcknowledgment(responseObserver, ResponseBuilder.buildMessageResponse(false));
                return;
            }
        } else {
            log.info("Received message from Node ID: {} to Node ID {}. Content: '{}'", request.getSenderId(),
                    request.getReceiverId(), request.getMessage());
        }
        Utils.sendAcknowledgment(responseObserver, ResponseBuilder.buildMessageResponse(true));
    }

    @Override
    public void startElection(ElectionRequest request, StreamObserver<ElectionResponse> responseObserver) {
        int candidateId = request.getNodeId();

        ElectionResponse electionResponse = ElectionResponse.newBuilder()
                .setAck(candidateId >= myNode.getNodeId())
                .build();

        Utils.sendAcknowledgment(responseObserver, electionResponse);
    }

    @Override
    public void announceLeader(LeaderAnnouncementRequest request, StreamObserver<LeaderAnnouncementResponse> responseObserver) {
        log.info("Got announce leader request! Setting a new leader...");
        int leaderPort = Constants.DEFAULT_PORT + request.getLeaderId();

        Address leaderAddress = new Address(
                Constants.HOSTNAME,
                leaderPort,
                request.getLeaderId()
        );

        myNode.getClient().getMyNeighbours().setLeaderAddress(leaderAddress);
        log.info("New leader with ID: {}, on port: {}", request.getLeaderId(), leaderPort);
        Utils.sendAcknowledgment(responseObserver, ResponseBuilder.buildLeaderAnnouncementResponse(true));
    }

    @Override
    public void isNodeAlive(AliveRequest request, StreamObserver<AliveResponse> responseObserver) {
        Utils.sendAcknowledgment(responseObserver, ResponseBuilder.buildALiveResponse(true));
    }


    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        topologyService.joinTopology(request, responseObserver);
    }

    @Override
    public void updateTopology(UpdateTopologyRequest request, StreamObserver<UpdateTopologyResponse> responseObserver) {
        if (this.myNode.isLeader()) {
            return;
        }

        topologyService.updateTopology(request.getAvailableNodesAddressesList());
    }


}