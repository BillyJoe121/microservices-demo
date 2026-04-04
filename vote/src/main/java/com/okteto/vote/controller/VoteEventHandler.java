package com.okteto.vote.controller;

/**
 * Patrón Observer: interfaz que define el contrato para cualquier
 * manejador que quiera ser notificado cuando se reciba un voto.
 *
 * Permite agregar nuevos comportamientos (logging, métricas, auditoría, Kafka)
 * sin modificar el VoteController.
 */
public interface VoteEventHandler {
    /**
     * Se invoca cada vez que un usuario emite un voto.
     *
     * @param voterId identificador único del votante (cookie)
     * @param vote    opción elegida (ej. "Burritos" o "Tacos")
     */
    void onVoteReceived(String voterId, String vote);
}
