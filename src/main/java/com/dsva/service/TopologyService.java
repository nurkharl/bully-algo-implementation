package com.dsva.service;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.ProtoModelBuilder;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.pattern.builder.ResponseBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;

@Slf4j
public class TopologyService {
    private final Node myNode;
    private final DSNeighbours myNeighbours;

    public TopologyService(Node myNode, DSNeighbours myNeighbours) {
        this.myNode = myNode;
        this.myNeighbours = myNeighbours;
    }

    public void joinTopology(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        if (!isValidJoinRequest(request)) {
            sendNegativeAcknowledgment(responseObserver);
            return;
        }
        processJoinRequest(request, responseObserver);
    }

    public void updateTopology(AvailableNodesAddressesList addressesToAdd) {
        addressesToAdd.getAddressesList().forEach(protoAddress -> {
            Address address = Utils.convertProtoModelToModelAddress(protoAddress);
            if (myNeighbours.isNodePresent(address)) {
                log.info("You are adding address that already exist in topology.");
            }
            myNeighbours.addNewNode(address);
            log.info("Processed node for topology update: {}", address);
        });
    }

    public void addNewNodeToTopology(JoinRequest request) {
        Address newNodeAddress = new Address(request.getHostname(), request.getPort(), request.getNodeId());
        myNode.getClient().getMyNeighbours().addNewNode(newNodeAddress);
    }

    public com.proto.chat_bully.Address getCurrentProtoLeader() {
        return com.proto.chat_bully.Address.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(myNeighbours.getLeaderAddress().port())
                .setNodeId(myNeighbours.getLeaderAddress().nodeId())
                .build();
    }

    public AvailableNodesAddressesList getCurrentAvailableNodesProtoAddresses() {
        HashSet<Address> currentAddresses = myNeighbours.getKnownNodes();
        AvailableNodesAddressesList.Builder availableNodesAddressesList = AvailableNodesAddressesList.newBuilder();

        for (Address nodeAddress : currentAddresses) {
            availableNodesAddressesList.addAddresses(ProtoModelBuilder.buildProtoAddress(nodeAddress.port(), nodeAddress.nodeId()));
        }

        availableNodesAddressesList.addAddresses(ProtoModelBuilder.buildProtoAddress(myNode.getClient().getMyAddress().port(), myNode.getNodeId()));

        return availableNodesAddressesList.build();
    }

    public void updateNodeTopology(Address address) throws NodeNotFoundException {
        int targetNodePort = myNeighbours.getTargetNodePort(address.nodeId());
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);

        try  {
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            UpdateTopologyRequest request = RequestBuilder.buildUpdateTopologyRequest(
                    getCurrentAvailableNodesProtoAddresses());
            UpdateTopologyResponse response = stub.updateTopology(request);

            if (!response.getAck()) {
                log.error("Network topology delivery failed or false ack received for node {}", address.nodeId());
            }
        } catch (Exception e) {
            log.error("Error sending gRPC updateTopology to node {}: {}", address.nodeId(), e.getMessage());
        } finally {
            channel.shutdown();
        }
    }

    private boolean isValidJoinRequest(JoinRequest request) {
        int joiningNodeId = request.getPort() - Constants.DEFAULT_PORT;
        return myNode.getNodeId() != joiningNodeId && myNode.getNodeId() >= 1 &&
                !myNode.getClient().getMyNeighbours().isNodePresent(joiningNodeId);
    }

    private void processJoinRequest(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        this.addNewNodeToTopology(request);
        sendPositiveAcknowledgment(responseObserver);
        myNode.getClient().sendCurrentTopology(request.getNodeId());
    }

    private void sendPositiveAcknowledgment(StreamObserver<JoinResponse> responseObserver) {
        JoinResponse joinResponse = ResponseBuilder.buildJoinResponse(true,
                this.getCurrentProtoLeader(),
                this.getCurrentAvailableNodesProtoAddresses()
        );
        Utils.sendAcknowledgment(responseObserver, joinResponse);
    }

    private void sendNegativeAcknowledgment(StreamObserver<JoinResponse> responseObserver) {
        JoinResponse joinResponse = ResponseBuilder.buildJoinResponse(false, null, null);
        Utils.sendAcknowledgment(responseObserver, joinResponse);
    }

}