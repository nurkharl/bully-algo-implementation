package com.dsva.pattern.command;

import com.dsva.Node;

public class HelpCommandHandler implements CommandHandler {

    @Override
    public void handle(String[] arguments, Node node) {
        System.out.println("? - this help");
        System.out.println("send - send message to Next neighbour");
        System.out.println("send <target node id> <message>");
        System.out.println("status - print this node status. Status includes network topology");
        System.out.println("quit - quit the current topology with notifying");
        System.out.println("kill - quit the current topology without notifying");
    }
}