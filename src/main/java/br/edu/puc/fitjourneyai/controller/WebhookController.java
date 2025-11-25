package br.edu.puc.fitjourneyai.controller;

import br.edu.puc.fitjourneyai.dto.internal.InternalMessage;
import br.edu.puc.fitjourneyai.dto.telegram.TelegramMessage;
import br.edu.puc.fitjourneyai.dto.telegram.TelegramUpdate;
import br.edu.puc.fitjourneyai.orchestrator.MessageOrchestrator;
import br.edu.puc.fitjourneyai.service.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final MessageOrchestrator messageOrchestrator;
    private final TelegramService telegramService;

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receiveUpdate(@RequestBody TelegramUpdate update) {
        log.info("Recebido update do Telegram: {}", update);

        if (update == null || update.getMessage() == null || update.getMessage().getChat() == null) {
            log.warn("Update invalido ou sem mensagem/chat: {}", update);
            return ResponseEntity.ok().build();
        }

        TelegramMessage msg = update.getMessage();

        Long chatId = msg.getChat().getId();
        String text = msg.getText() != null ? msg.getText() : "";
        Integer dateSeconds = msg.getDate();

        LocalDateTime dataHora = dateSeconds != null
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(dateSeconds), ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC);

        InternalMessage internalMessage = InternalMessage.builder()
                .chatId(chatId)
                .texto(text)
                .dataHora(dataHora)
                .build();

        String responseText = messageOrchestrator.handleIncomingMessage(internalMessage);

        if (responseText != null && !responseText.isBlank()) {
            telegramService.sendMessage(chatId, responseText);
        }

        // sempre 200 OK para o Telegram
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
