package com.dsva.service;

import com.dsva.Node;
import com.dsva.pattern.command.CommandHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HelpCommand implements CommandHandler {

    @Override
    public void handle(String[] arguments, Node node) {
        log.info("? - this help");
        log.info("h - send Hello message to Next neighbour");
        log.info("s - print node status");
    }
}
