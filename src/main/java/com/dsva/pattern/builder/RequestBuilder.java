package com.dsva.pattern.builder;

import com.dsva.model.Constants;
import com.proto.chat_bully.AvailableNodesAddressesList;
import com.proto.chat_bully.JoinRequest;
import com.proto.chat_bully.MessageRequest;
import com.proto.chat_bully.UpdateTopologyRequest;

public class RequestBuilder {

    private RequestBuilder() {
        throw new UnsupportedOperationException();
    }

    public static MessageRequest buildMessageRequest(String message, int senderNodeId, int receiverNodeId) {
        return MessageRequest.newBuilder()
                .setMessage(message)
                .setSenderId(senderNodeId)
                .setReceiverId(receiverNodeId)
                .build();
    }

    public static JoinRequest buildJoinRequest(int senderPort, int senderNodeId) {
        return JoinRequest.newBuilder()
                .setHostname(Constants.HOSTNAME)
                .setPort(senderPort)
                .setNodeId(senderNodeId)
                .build();
    }

    public static UpdateTopologyRequest buildUpdateTopologyRequest(AvailableNodesAddressesList availableNodesAddressesList) {
        return UpdateTopologyRequest.newBuilder()
                .setAvailableNodesAddressesList(availableNodesAddressesList)
                .build();
    }
}
