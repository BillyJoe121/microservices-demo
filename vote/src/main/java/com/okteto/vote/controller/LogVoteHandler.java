package com.okteto.vote.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Patrón Observer — Implementación concreta #2:
 * Registra el voto recibido en el log del sistema.
 */
public class LogVoteHandler implements VoteEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogVoteHandler.class);

    @Override
    public void onVoteReceived(String voterId, String vote) {
        logger.info("Vote received — voter: '{}', option: '{}'", voterId, vote);
    }
}
