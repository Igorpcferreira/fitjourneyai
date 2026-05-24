package br.edu.puc.fitjourneyai.adapter.telegram;

import br.edu.puc.fitjourneyai.adapter.telegram.dto.TelegramSendMessageRequest;
import br.edu.puc.fitjourneyai.config.TelegramProperties;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import br.edu.puc.fitjourneyai.core.util.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacao do MessageGateway para Telegram Bot API.
 * Converte automaticamente Markdown para HTML do Telegram.
 * Split automatico para mensagens maiores que 4096 caracteres.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramGateway implements MessageGateway {

    private static final int TELEGRAM_MAX_LENGTH = 4096;

    private final RestTemplate restTemplate;
    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void sendText(Long chatId, String text) {
        if (text == null || text.isBlank()) return;

        // Converte Markdown da IA para HTML do Telegram
        sendChunkedHtmlText(chatId, markdownToTelegramHtml(text));
    }

    private void sendSingleMessage(Long chatId, String text, String parseMode) {
        TelegramSendMessageRequest payload = TelegramSendMessageRequest.builder()
                .chatId(chatId).text(text).parseMode(parseMode)
                .disableNotification(false).build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(buildUrl("sendMessage"),
                    new HttpEntity<>(payload, headers), String.class);
        } catch (Exception ex) {
            // Se falhar com HTML, tenta sem parse_mode
            if (parseMode != null) {
                log.warn("Falha com parse_mode={}, tentando sem formatacao: {}", parseMode, ex.getMessage());
                try {
                    TelegramSendMessageRequest fallback = TelegramSendMessageRequest.builder()
                            .chatId(chatId).text(stripHtml(text)).disableNotification(false).build();
                    restTemplate.postForEntity(buildUrl("sendMessage"),
                            new HttpEntity<>(fallback, headers), String.class);
                } catch (Exception ex2) {
                    log.error("Erro ao enviar mensagem para chatId={}: {}", chatId, ex2.getMessage());
                }
            } else {
                log.error("Erro ao enviar mensagem para chatId={}: {}", chatId, ex.getMessage());
            }
        }
    }

    @Override
    public void sendHtmlText(Long chatId, String html) {
        if (html == null || html.isBlank()) return;
        sendChunkedHtmlText(chatId, html);
    }

    private void sendChunkedHtmlText(Long chatId, String htmlText) {
        List<String> parts = splitMessage(htmlText, TELEGRAM_MAX_LENGTH);
        for (String part : parts) {
            sendSingleMessage(chatId, part, "HTML");
        }
    }

    @Override
    public void sendPhoto(Long chatId, byte[] image, String caption) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("photo", createResource(image, "chart.png"));
        if (caption != null && !caption.isBlank()) {
            String htmlCaption = markdownToTelegramHtml(caption);
            body.add("caption", htmlCaption.length() > 1024 ? htmlCaption.substring(0, 1021) + "..." : htmlCaption);
            body.add("parse_mode", "HTML");
        }

        try {
            restTemplate.postForEntity(buildUrl("sendPhoto"),
                    new HttpEntity<>(body, headers), String.class);
        } catch (Exception ex) {
            log.error("Erro ao enviar foto para chatId={}: {}", chatId, ex.getMessage());
        }
    }

    @Override
    public void sendPhotoAlbum(Long chatId, List<byte[]> images, String caption) {
        if (images == null || images.isEmpty()) return;
        if (images.size() == 1) { sendPhoto(chatId, images.get(0), caption); return; }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);

        List<Map<String, Object>> mediaArray = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            String name = "photo" + i;
            Map<String, Object> media = new LinkedHashMap<>();
            media.put("type", "photo");
            media.put("media", "attach://" + name);
            if (i == 0 && caption != null && !caption.isBlank()) {
                media.put("caption", caption.length() > 1024 ? caption.substring(0, 1021) + "..." : caption);
                media.put("parse_mode", "HTML");
            }
            mediaArray.add(media);
            body.add(name, createResource(images.get(i), name + ".png"));
        }

        try {
            body.add("media", objectMapper.writeValueAsString(mediaArray));
            restTemplate.postForEntity(buildUrl("sendMediaGroup"),
                    new HttpEntity<>(body, headers), String.class);
        } catch (Exception ex) {
            log.error("Erro ao enviar album para chatId={}: {}", chatId, ex.getMessage());
            for (int i = 0; i < images.size(); i++) {
                sendPhoto(chatId, images.get(i), i == 0 ? caption : null);
            }
        }
    }

    // ========================================================================
    // FORMATACAO: Markdown -> HTML do Telegram
    // ========================================================================

    /**
     * Converte formatacao da IA para HTML do Telegram.
     * Aceita tanto Markdown (**bold**) quanto HTML (<b>bold</b>) da IA.
     * O Telegram aceita: b, i, u, s, code, pre, a
     */
    private String markdownToTelegramHtml(String text) {
        if (text == null) return null;

        String result = text;

        // Se a IA ja gerou HTML (<b>, <i>), usa direto sem escapar
        if (result.contains("<b>") || result.contains("<i>") || result.contains("<code>") || result.contains("<a ")) {
            return normalizeMarkdownLines(result, false);
        }

        // IA gerou Markdown puro - converte para HTML

        // 1. Escapa & < > do conteudo (protege URLs e texto)
        result = result.replace("&", "&amp;");
        // NAO escapa < e > aqui pois vamos criar tags

        // 2. Headings Markdown (## Titulo -> negrito) e remoção de separadores
        result = normalizeMarkdownLines(result, true);

        // 3. **bold** -> <b>bold</b>
        result = convertMarkdownBold(result);

        return result;
    }

    /**
     * Remove tags HTML (fallback se parse_mode falhar).
     */
    private String stripHtml(String text) {
        if (text == null) return null;
        return TextSanitizer.stripHtmlTags(text)
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private String normalizeMarkdownLines(String text, boolean wrapHeadings) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!isMarkdownSeparator(trimmed)) {
                int headingEnd = markdownHeadingPrefixEnd(line);
                if (headingEnd >= 0) {
                    String content = line.substring(headingEnd).trim();
                    out.append(wrapHeadings ? "<b>" + content + "</b>" : content);
                } else {
                    out.append(line);
                }
            }

            if (i < lines.length - 1) {
                out.append('\n');
            }
        }

        return out.toString();
    }

    private boolean isMarkdownSeparator(String trimmed) {
        if (trimmed.length() < 3) {
            return false;
        }
        char first = trimmed.charAt(0);
        if (first != '-' && first != '_') {
            return false;
        }
        for (int i = 1; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private int markdownHeadingPrefixEnd(String line) {
        int i = 0;
        while (i < line.length() && i < 3 && line.charAt(i) == '#') {
            i++;
        }
        if (i == 0 || i >= line.length() || !Character.isWhitespace(line.charAt(i))) {
            return -1;
        }
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return i;
    }

    private String convertMarkdownBold(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean boldOpen = false;

        for (int i = 0; i < text.length(); i++) {
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                out.append(boldOpen ? "</b>" : "<b>");
                boldOpen = !boldOpen;
                i++;
                continue;
            }
            out.append(text.charAt(i));
        }


        return out.toString();
    }

    // ========================================================================
    // SPLIT E UTILITARIOS
    // ========================================================================

    private List<String> splitMessage(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return List.of(text);
        }

        List<String> parts = new ArrayList<>();
        String remaining = text;

        while (remaining.length() > maxLength) {
            int splitAt = remaining.lastIndexOf("\n\n", maxLength);
            if (splitAt <= 0) splitAt = remaining.lastIndexOf("\n", maxLength);
            if (splitAt <= 0) splitAt = remaining.lastIndexOf(" ", maxLength);
            if (splitAt <= 0) splitAt = maxLength;

            parts.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }

        if (!remaining.isBlank()) {
            parts.add(remaining);
        }

        return parts;
    }

    private ByteArrayResource createResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override public String getFilename() { return filename; }
        };
    }

    private String buildUrl(String method) {
        return "%s/bot%s/%s".formatted(properties.getBaseUrl(), properties.getBotToken(), method);
    }
}
