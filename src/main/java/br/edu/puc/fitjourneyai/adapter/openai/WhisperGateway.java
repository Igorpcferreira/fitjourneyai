package br.edu.puc.fitjourneyai.adapter.openai;

import br.edu.puc.fitjourneyai.config.OpenAiProperties;
import br.edu.puc.fitjourneyai.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Gateway para transcrição de áudio via OpenAI Whisper API.
 * <p>
 * Fluxo: Telegram envia file_id do áudio (OGG/OGA) ->
 * baixa o arquivo via Telegram File API ->
 * envia para OpenAI /v1/audio/transcriptions ->
 * retorna texto transcrito.
 * <p>
 * Se qualquer etapa falhar, retorna Optional.empty() (fail-safe).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhisperGateway {

    private final RestTemplate restTemplate;
    private final OpenAiProperties openAiProperties;
    private final TelegramProperties telegramProperties;

    /**
     * Transcreve áudio a partir do file_id do Telegram.
     *
     * @param fileId identificador do arquivo de áudio no Telegram
     * @return texto transcrito, ou empty se falhar
     */
    public Optional<String> transcribe(String fileId) {
        return transcribe(fileId, null);
    }

    /**
     * Transcreve áudio a partir do file_id do Telegram com dica de MIME type.
     */
    public Optional<String> transcribe(String fileId, String mimeType) {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }

        try {
            // 1. Obter file_path do Telegram
            String filePath = getFilePath(fileId);
            if (filePath == null) {
                log.warn("Não foi possível obter file_path para fileId={}", fileId);
                return Optional.empty();
            }

            // 2. Baixar o arquivo de audio
            byte[] audioData = downloadFile(filePath);
            if (audioData == null || audioData.length == 0) {
                log.warn("Audio vazio para fileId={}", fileId);
                return Optional.empty();
            }

            log.info("Audio baixado: {} bytes, fileId={}", audioData.length, fileId);

            // 3. Enviar para Whisper API
            return transcribeAudio(audioData, filePath, mimeType);

        } catch (Exception e) {
            log.error("Erro ao transcrever audio fileId={}: {}", fileId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Obtém o file_path do Telegram a partir do file_id.
     */
    private String getFilePath(String fileId) {
        String url = "%s/bot%s/getFile?file_id=%s"
                .formatted(telegramProperties.getBaseUrl(), telegramProperties.getBotToken(), fileId);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                return null;
            }

            Object resultObj = body.get("result");
            if (!(resultObj instanceof Map<?, ?> result)) {
                return null;
            }

            Object filePathObj = result.get("file_path");
            return filePathObj instanceof String filePath ? filePath : null;
        } catch (Exception e) {
            log.error("Erro ao obter file_path: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Baixa o arquivo de áudio do Telegram.
     */
    private byte[] downloadFile(String filePath) {
        String url = "%s/file/bot%s/%s"
                .formatted(telegramProperties.getBaseUrl(), telegramProperties.getBotToken(), filePath);

        try {
            return restTemplate.getForObject(url, byte[].class);
        } catch (Exception e) {
            log.error("Erro ao baixar audio: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Envia áudio para a API Whisper da OpenAI para transcrição.
     */
    private Optional<String> transcribeAudio(byte[] audioData, String filePath, String mimeType) {
        String url = openAiProperties.getBaseUrl() + "/audio/transcriptions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openAiProperties.getApiKey());

        String filename = resolveFilename(filePath, mimeType);
        MediaType audioMediaType = resolveAudioMediaType(filename, mimeType);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource audioResource = new ByteArrayResource(audioData) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(audioMediaType);
        body.add("file", new HttpEntity<>(audioResource, fileHeaders));
        body.add("model", "whisper-1");
        body.add("language", "pt");
        body.add("response_format", "text");

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);

            String text = response.getBody();
            if (text != null && !text.isBlank()) {
                String normalized = text.trim();
                log.info("Whisper transcricao: '{}'", normalized.substring(0, Math.min(normalized.length(), 80)));
                return Optional.of(normalized);
            }
            log.warn("Whisper retornou resposta vazia para filePath={} mimeType={}", filePath, mimeType);
        } catch (Exception e) {
            log.error("Erro na transcricao Whisper: {}", e.getMessage());
        }

        return Optional.empty();
    }

    private String resolveFilename(String filePath, String mimeType) {
        String extension = ".ogg";

        if (filePath != null && filePath.contains(".")) {
            extension = filePath.substring(filePath.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }

        if (mimeType != null && !mimeType.isBlank()) {
            String lower = mimeType.toLowerCase(Locale.ROOT);
            if (lower.contains("ogg") || lower.contains("opus")) {
                extension = ".ogg";
            } else if (lower.contains("mpeg") || lower.contains("mp3")) {
                extension = ".mp3";
            } else if (lower.contains("wav")) {
                extension = ".wav";
            }
        }

        // Whisper aceita OGG; Telegram costuma enviar .oga para voice.
        if (".oga".equals(extension)) {
            extension = ".ogg";
        }

        return "audio" + extension;
    }

    private MediaType resolveAudioMediaType(String filename, String mimeType) {
        if (mimeType != null && !mimeType.isBlank()) {
            return MediaType.parseMediaType(mimeType);
        }

        if (filename.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (filename.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        return MediaType.parseMediaType("audio/ogg");
    }
}
