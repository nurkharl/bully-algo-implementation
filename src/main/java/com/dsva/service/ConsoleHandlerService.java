package com.dsva.service;

import com.dsva.Node;
import com.dsva.pattern.command.CommandHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConsoleHandlerService implements Runnable {
    private final Node node;
    private final Map<String, CommandHandler> commandHandlers;

    public ConsoleHandlerService(Node node) {
        this.node = node;
        this.commandHandlers = new HashMap<>();
        initializeCommands();
    }

    private void handleCommand(String command, String[] arguments) {
        CommandHandler handler = commandHandlers.get(command);
        if (handler != null) {
            handler.handle(arguments, node);
        } else {
            this.unknownCommand();
        }
    }

    private void initializeCommands() {
        commandHandlers.put("?", new HelpCommand());
        commandHandlers.put("h", new SendClientMessageCommand());
        commandHandlers.put("s", new StatusCommand());
    }

    private void parseCommandLine(String commandline) {
        String[] parts = commandline.split(" ");
        String command = parts[0];
        String[] arguments = Arrays.copyOfRange(parts, 1, parts.length);
        handleCommand(command, arguments);
    }

    private void unknownCommand() {
        log.info("Unrecognized command.");
    }

    @Override
    public void run() {
        handleCommand("?", new String[]{});
        System.out.println("You are myNode with id: " + node.getNodeId());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print(System.lineSeparator() + "cmd > ");

                String commandline = reader.readLine();
                if (commandline == null || commandline.equalsIgnoreCase("exit")) {
                    log.info("Exit from console handler.");
                    break;
                }

                parseCommandLine(commandline.trim());

            }
        } catch (IOException e) {
            log.error("Something went wrong while reading input from console.");
        }
        log.info("Closing ConsoleHandler.");
    }
}