package com.dsva.server;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.NodeState;
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

        if (myNode.getNodeState() == NodeState.QUITING) {
            return;
        }

        int candidateId = request.getNodeId();
        log.info("Received ElectionRequest from candidate: {}", candidateId);
        boolean isCandidateIdHigher = candidateId >= myNode.getNodeId();
        ElectionResponse electionResponse = ElectionResponse.newBuilder()
                .setAck(isCandidateIdHigher)
                .build();

        Utils.sendAcknowledgment(responseObserver, electionResponse);

        if (!isCandidateIdHigher) {
            log.info("My ID is higher than candidate's. I stop his election and take charge.");
            myNode.getClient().initiateElection();
        }
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
        log.info("Received AliveRequest from {}", request.getSenderNodeID());
        Utils.sendAcknowledgment(responseObserver, ResponseBuilder.buildALiveResponse(true));
    }


    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        topologyService.joinTopology(request, responseObserver);
    }

    @Override
    public void updateTopology(UpdateTopologyRequest request, StreamObserver<UpdateTopologyResponse> responseObserver) {
        log.info("Received update topology request from node id: {}",
                myNode.getClient().getMyNeighbours().getLeaderAddress().nodeId());
        if (this.myNode.isLeader()) {
            return;
        }

        topologyService.updateTopology(request.getAvailableNodesAddressesList(), responseObserver);
    }

    @Override
    public void quitTopology(QuitTopologyRequest request, StreamObserver<QuitTopologyResponse> responseObserver) {
        log.info("Received QuitTopologyRequest.");
        topologyService.removeNodeFromTopology(request, responseObserver);
    }
}