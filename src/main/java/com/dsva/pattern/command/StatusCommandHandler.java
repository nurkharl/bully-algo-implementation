package com.dsva.pattern.command;

import com.dsva.Node;

public class StatusCommandHandler implements CommandHandler {
    @Override
    public void handle(String[] arguments, Node node) {
        node.printStatus();
    }
}
