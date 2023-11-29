package com.dsva.pattern.command;

import com.dsva.Node;

public interface CommandHandler {
    void handle(String[] arguments, Node node);
}