package com.dsva.service;

import com.dsva.Node;
import com.dsva.pattern.command.CommandHandler;

public class StatusCommand implements CommandHandler {
    @Override
    public void handle(String[] arguments, Node node) {
        node.printStatus();
    }
}
