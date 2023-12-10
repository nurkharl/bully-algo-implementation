package com.dsva.pattern.builder;

import com.dsva.model.Constants;
import com.dsva.model.DSNeighbours;

public class ProtoModelBuilder {

    private ProtoModelBuilder() { throw new UnsupportedOperationException(); }

    public static com.proto.chat_bully.Address buildProtoAddress(int port, int nodeId) {
        return com.proto.chat_bully.Address.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(port)
                .setNodeId(nodeId)
                .build();
    }

    public static com.proto.chat_bully.Address buildProtoLeader(DSNeighbours myNeighbours) {
        return com.proto.chat_bully.Address.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(myNeighbours.getLeaderAddress().port())
                .setNodeId(myNeighbours.getLeaderAddress().nodeId())
                .build();
    }
}
