package com.dsva.model;

import com.dsva.exception.NodeNotFoundException;
import com.dsva.pattern.builder.ProtoModelBuilder;
import com.proto.chat_bully.AvailableNodesAddressesList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class DSNeighbours {
    private ConcurrentHashMap<Integer, Address> knownNodes;
    private Address leaderAddress;

    public DSNeighbours(Address leaderAddress) {
        this.leaderAddress = leaderAddress;
        this.knownNodes = new ConcurrentHashMap<>();
    }

    @Override
    public String toString() {
        String reset = "\u001B[0m";
        String colorGreen = "\u001B[32m";
        String colorYellow = "\u001B[33m";
        String colorCyan = "\u001B[36m";

        return colorCyan + "Current Topology:\n" + reset +
                colorGreen + "Known Nodes: " + reset +
                knownNodes + "\n" +
                colorYellow + "Leader: " + reset +
                leaderAddress;
    }


    public Address getTargetNodeAddress(Integer nodeId) throws NodeNotFoundException {
        Address address = knownNodes.get(nodeId);

        if (address != null) {
            return address;
        }

        throw new NodeNotFoundException("Trying to access not existing node: " + nodeId);
    }

    public void addNewNode(@NonNull Address address) {
        if (isAddressValid(address)) {
            knownNodes.put(address.nodeId(), address);
            log.info("Adding new Node{hostname:{}, port:{}, nodeId:{}}",
                    address.hostname(), address.port(), address.nodeId());
            log.info("Topology after adding: {}", knownNodes.values());
        }
    }

    public void removeNode(@NonNull Integer nodeId) {
        if (isNodeIdValid(nodeId) && knownNodes.containsKey(nodeId)) {
            knownNodes.remove(nodeId);
            log.info("Node with id: {} was successfully removed. Current topology:\n{}", nodeId, knownNodes);
        } else {
            log.warn("Cannot remove node with id {}", nodeId);
            log.warn(toString());
        }
    }

    public boolean isNodePresent(int nodeId) {
        return knownNodes.containsKey(nodeId);
    }

    public com.proto.chat_bully.Address getCurrentProtoLeader() {
        return ProtoModelBuilder.buildProtoLeader(this);
    }

    public AvailableNodesAddressesList getCurrentAvailableNodesProtoAddresses(int myPort, int myNodeId, int senderNodId, String myHostname) {
        AvailableNodesAddressesList.Builder availableNodesAddressesList = AvailableNodesAddressesList.newBuilder();

        for (Address nodeAddress : knownNodes.values()) {
            if (nodeAddress.nodeId() != senderNodId) {
                availableNodesAddressesList.addAddresses(ProtoModelBuilder.buildProtoAddress(nodeAddress.port(), nodeAddress.nodeId(), nodeAddress.hostname()));
            }
        }

        availableNodesAddressesList.addAddresses(ProtoModelBuilder.buildProtoAddress(myPort, myNodeId, myHostname));
        return availableNodesAddressesList.build();
    }

    public HashSet<Address> getHigherNodes(int myNodeId) {
        return knownNodes.values().stream()
                .filter(address -> address.nodeId() > myNodeId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean isAddressValid(Address address) {
        if (address.port() < 1 || !isNodeIdValid(address.nodeId())) {
            log.warn("Attempted to add null or invalid address to known nodes.");
            return false;
        }
        return true;
    }

    private boolean isNodeIdValid(int nodeId) {
        return nodeId >= 0;
    }
}