package com.dsva;

import com.dsva.client.Client;
import com.dsva.model.Address;
import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;
import com.dsva.server.ServerImpl;
import com.dsva.service.ConsoleHandlerService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;

@Slf4j
public class Node {
    @Getter
    private final Integer nodeId;
    private Server server;
    @Getter
    private Client client;
    private final ConsoleHandlerService consoleHandlerService;
    private Thread consoleHandlerThread;

    public static void main(String[] args) throws IOException, InterruptedException {
        Node node = new Node(args);
        node.startServer();
        node.startConsoleHandler();
        node.server.awaitTermination();
    }

    public Node(String[] args) {
        this.nodeId = Integer.parseInt(args[0]);
        this.consoleHandlerService = new ConsoleHandlerService(this);
        this.setUpNodeNetworkProperties(nodeId);
    }

    private void startConsoleHandler() {
        consoleHandlerThread = new Thread(consoleHandlerService);
        consoleHandlerThread.start();
    }

    public void startServer() throws IOException {
        int port = this.client.myAddress().port();

        server = ServerBuilder.forPort(port)
                .addService(new ServerImpl(this))
                .build()
                .start();

        log.info("Server started on port: " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.error("*** shutting down gRPC server since JVM is shutting down");
            Node.this.stopServer();
            log.error("*** server shut down");
        }));
    }

    private void stopServer() {
        if (server != null) {
            server.shutdown();
        }
        if (consoleHandlerThread != null) {
            consoleHandlerThread.interrupt();
        }
    }

    private void setUpNodeNetworkProperties(Integer nodeId) {
        int generatedPort = Constants.DEFAULT_PORT + nodeId;
        Address myAddress = new Address(Constants.HOSTNAME, generatedPort, this.getNodeId());

        client = new Client(
                myAddress,
                setUpNodeNeighboursAddresses(myAddress),
                this
        );
    }

    private DSNeighbours setUpNodeNeighboursAddresses(Address myAddress) {
        HashSet<Address> neighboursNodesIds = new HashSet<>();

        for (Integer neighbourNodeId : Constants.initialNodesIdsSet) {
            if (Objects.equals(neighbourNodeId, this.getNodeId())) {
                continue;
            }
            neighboursNodesIds.add(new Address(
                    Constants.HOSTNAME,
                    Constants.DEFAULT_PORT + neighbourNodeId,
                    neighbourNodeId
            ));
        }

        return new DSNeighbours(neighboursNodesIds, myAddress);
    }

    public void printStatus() {
        log.info(String.format("Node id: %d", nodeId));
        log.info(client.myNeighbours().toString());
    }
}