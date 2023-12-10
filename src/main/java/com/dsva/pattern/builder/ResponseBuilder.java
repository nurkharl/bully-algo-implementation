package com.dsva.pattern.builder;

import com.proto.chat_bully.*;

public class ResponseBuilder {
    private ResponseBuilder() { throw new UnsupportedOperationException(); }

    public static JoinResponse buildJoinResponse(boolean ack,
                                           com.proto.chat_bully.Address protoLeader,
                                           AvailableNodesAddressesList availableNodesAddressesList ) {
        return JoinResponse.newBuilder()
                .setAck(ack)
                .setLeader(protoLeader)
                .setAvailableNodesAddressesList(availableNodesAddressesList)
                .build();
    }

    public static MessageResponse buildMessageResponse(boolean ack) {
        return MessageResponse.newBuilder()
                .setAck(ack)
                .build();
    }

    public static AliveResponse buildALiveResponse(boolean ack) {
        return AliveResponse.newBuilder()
                .setAck(ack)
                .build();
    }

    public static LeaderAnnouncementResponse buildLeaderAnnouncementResponse(boolean ack) {
        return LeaderAnnouncementResponse.newBuilder()
                .setAck(ack)
                .build();
    }
}
