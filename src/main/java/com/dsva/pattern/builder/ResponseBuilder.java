package com.dsva.pattern.builder;

import com.proto.chat_bully.AvailableNodesAddressesList;
import com.proto.chat_bully.JoinResponse;
import com.proto.chat_bully.MessageResponse;

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

    public static JoinResponse buildJoinResponse(boolean ack) {
        return JoinResponse.newBuilder()
                .setAck(ack)
                .build();
    }

    public static MessageResponse buildMessageResponse(boolean ack) {
        return MessageResponse.newBuilder()
                .setAck(ack)
                .build();
    }
}
