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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class Node {
    @Getter
    private final Integer nodeId;
    @Getter @Setter
    private boolean isLeader;
    private Server server;
    @Getter
    private Client client;
    private final ConsoleHandlerService consoleHandlerService = new ConsoleHandlerService(this);
    private Thread consoleHandlerThread;

    public static void main(String[] args) throws IOException, InterruptedException {
        Node node = new Node(args);
        node.startServer();
        node.startConsoleHandler();
        node.server.awaitTermination();
    }

    public Node(String[] args) {
        this.nodeId = Integer.parseInt(args[0]);
        this.isLeader = nodeId == 5;
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
        DSNeighbours myNeighbours = new DSNeighbours(myAddress);

        client = new Client(
                myAddress,
                myNeighbours,
                this
        );

        client.joinNetworkTopology();
    }

    public void printStatus() {
        log.info(String.format("Node id: %d", nodeId));
        log.info(client.myNeighbours().toString());
    }
}