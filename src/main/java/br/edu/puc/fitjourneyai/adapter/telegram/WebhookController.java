package br.edu.puc.fitjourneyai.adapter.telegram;

import br.edu.puc.fitjourneyai.adapter.openai.WhisperGateway;
import br.edu.puc.fitjourneyai.adapter.telegram.dto.TelegramUpdate;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.orchestrator.ConversationOrchestrator;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST que recebe webhooks do Telegram.
 * <p>
 * Suporta mensagens de texto e audio (voz).
 * Audios sao transcritos via OpenAI Whisper antes de serem processados.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final ConversationOrchestrator orchestrator;
    private final MessageGateway messageGateway;
    private final WhisperGateway whisperGateway;

    @PostMapping("${telegram.webhook-path:/telegram/webhook}")
    public ResponseEntity<Void> onUpdate(@RequestBody TelegramUpdate update) {
        if (update.getMessage() == null || update.getMessage().getChat() == null) {
            return ResponseEntity.ok().build();
        }

        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText();

        // Se e audio de voz, transcreve via Whisper
        if (update.getMessage().hasVoice()) {
            text = transcribeVoice(chatId, update.getMessage().getVoice().getFileId());
            if (text == null) {
                return ResponseEntity.ok().build();
            }
        }

        // Se nao tem texto (nem de voz nem de texto), ignora
        if (text == null || text.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            FlowResult result = orchestrator.handleMessage(chatId, text);

            if (result == null) {
                return ResponseEntity.ok().build();
            }

            if (result.hasImage()) {
                messageGateway.sendPhoto(chatId, result.imageData(), result.imageCaption());
            }
            if (result.responseText() != null && !result.responseText().isBlank()) {
                messageGateway.sendText(chatId, result.responseText());
            }

        } catch (Exception e) {
            log.error("Erro ao processar update para chatId={}: {}", chatId, e.getMessage(), e);
            try {
                messageGateway.sendText(chatId,
                        "Desculpe, tive um problema tecnico. Tente novamente com /menu.");
            } catch (Exception sendError) {
                log.error("Erro ao enviar fallback: {}", sendError.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Transcreve audio de voz via Whisper e notifica o usuario.
     * Retorna o texto transcrito ou null se falhar.
     */
    private String transcribeVoice(Long chatId, String fileId) {
        try {
            log.info("Transcrevendo audio: chatId={}, fileId={}", chatId, fileId);

            return whisperGateway.transcribe(fileId)
                    .map(transcription -> {
                        log.info("Audio transcrito: chatId={}, texto='{}'",
                                chatId, transcription.substring(0, Math.min(transcription.length(), 50)));
                        return transcription;
                    })
                    .orElseGet(() -> {
                        messageGateway.sendText(chatId,
                                "Nao consegui entender o audio. Pode tentar de novo ou mandar por texto?");
                        return null;
                    });

        } catch (Exception e) {
            log.error("Erro na transcricao de audio: chatId={}, error={}", chatId, e.getMessage());
            messageGateway.sendText(chatId,
                    "Tive um problema ao processar seu audio. Tenta mandar por texto!");
            return null;
        }
    }
}
