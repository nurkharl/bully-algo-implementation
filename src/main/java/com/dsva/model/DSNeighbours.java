package com.dsva.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class DSNeighbours {
    private final Set<Address> knownNodes;
    private Address leaderAddress;

    public DSNeighbours(Address leaderAddress) {
        this.leaderAddress = leaderAddress;
        this.knownNodes = new HashSet<>();
    }

    @Override
    public String toString() {
        return "BullyNodeInfo{" +
                ", knownNodes=" + knownNodes +
                ", leaderId=" + leaderAddress +
                '}';
    }

    public int getTargetNodePort(Integer nodeId) {
        int expectedTargetPort = nodeId + Constants.DEFAULT_PORT;

        for (Address address : knownNodes) {
            if (Objects.equals(address.port(), expectedTargetPort)) {
                return expectedTargetPort;
            }
        }
        log.error("Trying to access not existing target port. Maybe leader is dead.");
        return -1;
    }
}