package com.dsva.pattern.command;

import com.dsva.Node;

public class HelpCommandHandler implements CommandHandler {

    @Override
    public void handle(String[] arguments, Node node) {
        String reset = "\u001B[0m";
        String colorBlue = "\u001B[34m";
        String colorYellow = "\u001B[33m";
        String bold = "\u001B[1m";

        System.out.println(colorBlue + bold + "? - " + reset + colorYellow + "this help" + reset);
        System.out.println(colorBlue + bold + "send - " + reset + colorYellow + "send message to Next neighbour" + reset);
        System.out.println(colorBlue + bold + "send <target node id> <message> - " + reset + colorYellow + "send a specific message to a specific node" + reset);
        System.out.println(colorBlue + bold + "status - " + reset + colorYellow + "print this node status. Status includes network topology" + reset);
        System.out.println(colorBlue + bold + "quit - " + reset + colorYellow + "quit the current topology **with** notifying" + reset);
        System.out.println(colorBlue + bold + "quit --force - " + reset + colorYellow + "quit **without** notifying" + reset);

    }
}