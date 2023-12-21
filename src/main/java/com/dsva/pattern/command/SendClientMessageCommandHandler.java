package com.dsva.pattern.command;

import com.dsva.Node;
import com.dsva.exception.NodeNotFoundException;
import com.dsva.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class SendClientMessageCommandHandler implements CommandHandler {

    private static final int EXPECTED_ARGUMENTS = 2;

    @Override
    public void handle(String[] arguments, Node node) {
        if (!Utils.areArgumentsValid(arguments, EXPECTED_ARGUMENTS)) {
            return;
        }

        try {
            int nodeId = Integer.parseInt(arguments[0]);
            String message = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));

            if (message.isEmpty()) {
                log.warn("Message is blank or empty. Please write something! :)");
                return;
            }

            node.getClient().sendMessage(nodeId, message);
        } catch (NumberFormatException e) {
            log.error("Node ID should be number! Try again!");
        } catch (NodeNotFoundException e) {
            System.out.println("You are trying to send a message to a Node not existing in this topology." +
                    " Print status to see available nodes.");
        }
    }

}