package com.okteto.vote.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;

/**
 * Patrón Observer — Implementación concreta #1:
 * Maneja el envío del voto al tópico de Kafka.
 */
public class KafkaVoteHandler implements VoteEventHandler {

    private static final String KAFKA_TOPIC = "votes";
    private static final Logger logger = LoggerFactory.getLogger(KafkaVoteHandler.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaVoteHandler(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void onVoteReceived(String voterId, String vote) {
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KAFKA_TOPIC, voterId, vote);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Message [{}] delivered with offset {}",
                        vote, result.getRecordMetadata().offset());
            } else {
                logger.warn("Unable to deliver message [{}]. {}", vote, ex.getMessage());
            }
        });
    }
}
