package com.dsva.model;

import com.dsva.exception.NodeNotFoundException;
import com.dsva.pattern.builder.ProtoModelBuilder;
import com.dsva.util.Utils;
import com.proto.chat_bully.AvailableNodesAddressesList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class DSNeighbours {
    private HashSet<Address> knownNodes;
    private Address leaderAddress;

    public DSNeighbours(Address leaderAddress) {
        this.leaderAddress = leaderAddress;
        this.knownNodes = new HashSet<>();
    }

    @Override
    public String toString() {
        return "BullyNodeInfo{" +
                ", knownNodes=" + knownNodes +
                ",\n leaderId=" + leaderAddress +
                '}';
    }

    public int getTargetNodePort(Integer nodeId) throws NodeNotFoundException {
        int expectedTargetPort = Utils.getNodePortFromNodeId(nodeId);

        for (Address address : knownNodes) {
            if (Objects.equals(address.port(), expectedTargetPort)) {
                return expectedTargetPort;
            }
        }

        throw new NodeNotFoundException("Trying to access not existing target port: " + expectedTargetPort);
    }

    public void addNewNode(@NonNull Address address) {
        if (isAddressValid(address)) {
            knownNodes.add(address);
            log.info("Adding new Node{hostname:{}, port:{}, nodeId:{}}",
                    address.hostname(), address.port(), address.nodeId());
            log.info("Topology after adding: {}", knownNodes);
        }
    }

    public void removeNode(@NonNull Address address) {
        if (isAddressValid(address)) {
            knownNodes.remove(address);
            log.info("Removing myNode{hostname:{}, port:{}, nodeId:{}}",
                    address.hostname(), address.port(), address.nodeId());
        }
    }

    public boolean isNodePresent(Address nodeAddress) {
        return knownNodes.contains(nodeAddress);
    }

    public boolean isNodePresent(int nodeId) {
        return knownNodes.stream().anyMatch(node -> node.nodeId() == nodeId);
    }

    public com.proto.chat_bully.Address getCurrentProtoLeader() {
        return ProtoModelBuilder.buildProtoLeader(this);
    }

    public AvailableNodesAddressesList getCurrentAvailableNodesProtoAddresses(int myPort, int myNodeId) {
        HashSet<Address> currentAddresses = getKnownNodes();
        AvailableNodesAddressesList.Builder availableNodesAddressesList = AvailableNodesAddressesList.newBuilder();

        for (Address nodeAddress : currentAddresses) {
            availableNodesAddressesList.addAddresses(ProtoModelBuilder.buildProtoAddress(nodeAddress.port(), nodeAddress.nodeId()));
        }

        availableNodesAddressesList.addAddresses(ProtoModelBuilder.buildProtoAddress(myPort, myNodeId));

        return availableNodesAddressesList.build();
    }

    private boolean isAddressValid(Address address) {
        if (address.port() < 1 && address.nodeId() < 0) {
            log.warn("Attempted to add null or invalid address to known nodes.");
            return false;
        }

        return true;
    }
}