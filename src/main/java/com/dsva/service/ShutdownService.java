package com.dsva.service;

import com.dsva.client.Client;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.pattern.builder.RequestBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.*;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ShutdownService {

    private final Client client;

    public void quitTopologyWithNotification(int senderNodeId) throws NodeNotFoundException {
        int targetNodePort = client.getMyNeighbours().getTargetNodePort(client.getMyNeighbours().getLeaderAddress().port());
        ManagedChannel channel = Utils.buildManagedChannel(targetNodePort);

        try {
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            QuitTopologyRequest request = RequestBuilder.buildQuitTopologyRequest(senderNodeId);

            QuitTopologyResponse messageResponse = stub.quitTopology(request);

            if (messageResponse.getAck()) {
                log.info("Leader know node is quiting the topology. Quiting the topology...");
                systemExit();
            } else {
                log.error("Leader responded with false ack!");
            }
        } catch (Exception e) {
            log.error("Error while quiting the topology: {}", e.getMessage());
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
