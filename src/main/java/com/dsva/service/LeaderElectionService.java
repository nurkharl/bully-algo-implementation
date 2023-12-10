package com.dsva.service;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class LeaderElectionService {
    private final DSNeighbours myNeighbours;
    private final Node myNode;

    public void initiateElection() {
        List<Integer> higherNodes = myNeighbours.getKnownNodes().stream()
                .map(Address::nodeId)
                .filter(nodeId -> nodeId > myNode.getNodeId())
                .toList();

        if (higherNodes.isEmpty()) {
            becomeLeader();
            return;
        }

        CountDownLatch latch = new CountDownLatch(higherNodes.size());
        AtomicBoolean higherNodeFound = new AtomicBoolean(false);

        for (Integer nodeId : higherNodes) {
            checkIfNodeIsAlive(nodeId, alive -> {
                if (Boolean.TRUE.equals(alive)) {
                    higherNodeFound.set(true);
                }
                latch.countDown();
            });
        }

        try {
            latch.await();
            if (!higherNodeFound.get()) {
                becomeLeader();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Election process was interrupted", e);
        }
    }

    public void checkIfNodeIsAlive(int targetNodeId, MessagePassingQueue.Consumer<Boolean> onResult) {
        int targetNodePort;
        try {
            targetNodePort = myNeighbours.getTargetNodePort(targetNodeId);
        } catch (NodeNotFoundException e) {
            log.error(e.getMessage());
            return;
        }
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);

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
    }


    private void becomeLeader() {
        myNode.setLeader(true);
        announceLeadership();
        myNode.getClient().getMyNeighbours().setLeaderAddress(myNode.getClient().getMyAddress());
        log.info("Your node with honor became a leader! Glory to the new leader!");
    }

    private void announceLeadership() {
        for (Address address : myNeighbours.getKnownNodes()) {
            if (address.nodeId() < myNode.getNodeId()) {
                announceLeadershipToNode(address);
            }
        }
    }

    private void announceLeadershipToNode(Address address) {
        int targetNodePort = Utils.getNodePortFromNodeId(address.nodeId());
        try {
            ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            LeaderAnnouncementRequest request = RequestBuilder.buildLeaderAnnouncementRequest(myNode.getNodeId());
            LeaderAnnouncementResponse response = stub.announceLeader(request);

            if (response.getAck()) {
                log.info("Node {} acknowledged your leadership", address.nodeId());
            } else {
                log.warn("Node {} did not acknowledge your leadership", address.nodeId());
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC error announcing leadership to node {}: {}", address.nodeId(), e.getStatus());
        } catch (Exception e) {
            log.error("Error announcing leadership to node {}: {}", address.nodeId(), e.getMessage());
        }
    }

}
