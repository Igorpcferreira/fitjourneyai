package br.edu.puc.fitjourneyai.service.telegram;

import br.edu.puc.fitjourneyai.config.TelegramProperties;
import br.edu.puc.fitjourneyai.dto.telegram.TelegramSendMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final RestTemplate restTemplate;
    private final TelegramProperties telegramProperties;

    private String buildSendMessageUrl() {
        // Exemplo: https://api.telegram.org/bot<token>/sendMessage
        return telegramProperties.getBaseUrl()
                + "/bot"
                + telegramProperties.getBotToken()
                + "/sendMessage";
    }

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null, false);
    }

    public void sendMessageMarkdown(Long chatId, String text) {
        sendMessage(chatId, text, "Markdown", false);
    }

    public void sendMessage(Long chatId, String text, String parseMode, boolean disableNotification) {
        TelegramSendMessageRequest payload = TelegramSendMessageRequest.builder()
                .chatId(chatId)
                .text(text)
//                .parseMode(parseMode)
                .disableNotification(disableNotification)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TelegramSendMessageRequest> requestEntity = new HttpEntity<>(payload, headers);

        String url = buildSendMessageUrl();

        try {
            restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("Mensagem enviada para chatId {}: {}", chatId, text);
        } catch (Exception ex) {
            log.error("Erro ao enviar mensagem para Telegram (chatId={}): {}", chatId, ex.getMessage(), ex);
        }
    }
}
