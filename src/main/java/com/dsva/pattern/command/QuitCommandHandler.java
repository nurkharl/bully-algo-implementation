package com.dsva.pattern.command;

import com.dsva.Node;
import com.dsva.model.NodeState;

public class QuitCommandHandler implements CommandHandler {

    @Override
    public void handle(String[] arguments, Node node) {
        node.setNodeState(NodeState.QUITING);
        if (arguments.length > 0 && arguments[0].equals("--force")) {
            forceQuit(node);
        } else {
            quit(node, node.getNodeId());
        }
    }

    private void quit(Node node, int senderNodeId) {
        node.getClient().quitTopologyWithNotification(senderNodeId);
    }

    private void forceQuit(Node node) {
        node.getClient().quitTopologyWithoutNotification();
    }
}