package com.dsva.service;

import com.dsva.client.Client;
import com.dsva.model.Address;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.NodeGrpc;
import com.proto.chat_bully.QuitTopologyRequest;
import com.proto.chat_bully.QuitTopologyResponse;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ShutdownService {

    private final Client client;

    public void quitTopologyWithNotification(int senderNodeId) {
        Address leaderAddress = client.getLeaderAddress();
        if (leaderAddress.equals(client.getMyAddress())) {
            quitTopologyAsALeader();
        } else {
            boolean shouldShutdown = sendQuitTopologyRequest(senderNodeId, leaderAddress);
            if (shouldShutdown) {
                log.info("Leader knows I am quiting...");
                systemExit();
            }
        }
    }

    public void quitTopologyAsALeader() {
        log.info("Quiting topology as a leader.");
        boolean shouldShutdown = client.getMyNeighbours().getKnownNodes().values().stream()
                .anyMatch(address -> sendQuitTopologyRequest(client.getMyAddress().nodeId(), address));

        if (shouldShutdown) {
            log.info("Nodes are aware of my quiting... Bye bye");
            systemExit();
        }
    }

    private boolean sendQuitTopologyRequest(int senderNodeId, Address targetNodeAddress) {
        log.info("Sending QuitTopologyRequest to node: {}", targetNodeAddress.nodeId());
        ManagedChannel channel = Utils.buildManagedChannel(targetNodeAddress.port(), targetNodeAddress.hostname());
        try {
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            QuitTopologyRequest request = RequestBuilder.buildQuitTopologyRequest(senderNodeId);
            QuitTopologyResponse messageResponse = stub.quitTopology(request);

            return messageResponse.getAck();
        } catch (Exception e) {
            log.error("Error while quiting the topology: {}", e.getMessage());
            return false;
        } finally {
            channel.shutdown();
            Utils.awaitChannelTermination(channel);
        }
    }


    public void quitTopologyWithoutNotification() {
        systemExit();
    }

    private void systemExit() {
        System.exit(0);
    }
}