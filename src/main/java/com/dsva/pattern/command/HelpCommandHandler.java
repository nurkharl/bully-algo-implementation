package com.dsva.pattern.command;

import com.dsva.Node;

public class HelpCommandHandler implements CommandHandler {

    @Override
    public void handle(String[] arguments, Node node) {
        System.out.println("? - this help");
        System.out.println("h - send message to Next neighbour");
        System.out.println("h <id of target node> <Message>");
        System.out.println("s - print this node status. Status includes network topology");
    }
}