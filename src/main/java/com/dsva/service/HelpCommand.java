package com.dsva.service;

import com.dsva.Node;
import com.dsva.pattern.command.CommandHandler;

public class HelpCommand implements CommandHandler {

    @Override
    public void handle(String[] arguments, Node node) {
        System.out.println("? - this help");
        System.out.println("h - send message to Next neighbour");
        System.out.println("h <id of target node> <Message>");
        System.out.println("s - print this node status. Status includes network topology");
    }
}