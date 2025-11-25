package br.edu.puc.fitjourneyai.orchestrator;

import lombok.Value;

import java.time.Instant;

@Value
public class IncomingMessage {
    Long chatId;
    String text;
    Instant dateTime;
}
