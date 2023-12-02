package com.dsva.service;

import com.dsva.Node;
import com.dsva.pattern.command.CommandHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class SendClientMessageCommand implements CommandHandler {

    private static final int EXPECTED_ARGUMENTS = 2;

    @Override
    public void handle(String[] arguments, Node node) {
        if (!areArgumentsValid(arguments)) {
            return;
        }

        try {
            int nodeId = Integer.parseInt(arguments[0]);
            String message = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));

            if (message.isEmpty()) {
                log.warn("Message is blank or empty. Please write something! :)");
                return;
            }

            node.getClient().sendClientMessage(nodeId, message);
        } catch (NumberFormatException e) {
            log.error("Node ID should be number! Try again!");
        }
    }

    private boolean areArgumentsValid(String[] arguments) {
        if (arguments.length < EXPECTED_ARGUMENTS) {
            log.error("Insufficient arguments. Expected {}, but received {}: {}",
                    EXPECTED_ARGUMENTS, arguments.length, Arrays.toString(arguments));
            return false;
        }
        return true;
    }
}