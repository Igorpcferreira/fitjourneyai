package br.edu.puc.fitjourneyai.adapter.openai;

import br.edu.puc.fitjourneyai.config.OpenAiProperties;
import br.edu.puc.fitjourneyai.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhisperGatewayTest {

    @Mock private RestTemplate restTemplate;
    @Mock private OpenAiProperties openAiProperties;
    @Mock private TelegramProperties telegramProperties;

    private WhisperGateway gateway;

    @BeforeEach
    void setUp() {
        lenient().when(telegramProperties.getBaseUrl()).thenReturn("https://api.telegram.org");
        lenient().when(telegramProperties.getBotToken()).thenReturn("test-token");
        lenient().when(openAiProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        lenient().when(openAiProperties.getApiKey()).thenReturn("test-key");
        gateway = new WhisperGateway(restTemplate, openAiProperties, telegramProperties);
    }

    @Test
    @DisplayName("Deve retornar vazio para fileId null")
    void deveRetornarVazioParaNull() {
        assertThat(gateway.transcribe(null)).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio para fileId vazio")
    void deveRetornarVazioParaVazio() {
        assertThat(gateway.transcribe("")).isEmpty();
    }

    @Test
    @DisplayName("Deve transcrever audio com sucesso")
    void deveTranscreverSucesso() {
        // Mock getFile
        Map<String, Object> fileResult = Map.of("file_path", "voice/file_0.oga");
        Map<String, Object> fileResponse = Map.of("ok", true, "result", fileResult);
        when(restTemplate.getForEntity(contains("getFile"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(fileResponse));

        // Mock download
        when(restTemplate.getForObject(contains("file/bot"), eq(byte[].class)))
                .thenReturn(new byte[]{1, 2, 3, 4});

        // Mock Whisper transcription
        when(restTemplate.postForEntity(contains("audio/transcriptions"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("quero um treino de pernas"));

        Optional<String> result = gateway.transcribe("file123");
        assertThat(result).contains("quero um treino de pernas");
    }

    @Test
    @DisplayName("Deve retornar vazio quando getFile falha")
    void deveRetornarVazioQuandoGetFileFalha() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Telegram error"));

        assertThat(gateway.transcribe("file123")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando getFile retorna body nulo")
    void deveRetornarVazioQuandoGetFileBodyNulo() {
        when(restTemplate.getForEntity(contains("getFile"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThat(gateway.transcribe("file123")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando getFile retorna result invalido")
    void deveRetornarVazioQuandoGetFileResultInvalido() {
        when(restTemplate.getForEntity(contains("getFile"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ok", true, "result", "invalido")));

        assertThat(gateway.transcribe("file123")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando download falha")
    void deveRetornarVazioQuandoDownloadFalha() {
        Map<String, Object> fileResult = Map.of("file_path", "voice/file.oga");
        when(restTemplate.getForEntity(contains("getFile"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ok", true, "result", fileResult)));
        when(restTemplate.getForObject(anyString(), eq(byte[].class)))
                .thenReturn(null);

        assertThat(gateway.transcribe("file123")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando Whisper falha")
    void deveRetornarVazioQuandoWhisperFalha() {
        Map<String, Object> fileResult = Map.of("file_path", "voice/file.oga");
        when(restTemplate.getForEntity(contains("getFile"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ok", true, "result", fileResult)));
        when(restTemplate.getForObject(anyString(), eq(byte[].class)))
                .thenReturn(new byte[]{1, 2, 3});
        when(restTemplate.postForEntity(contains("audio"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Whisper error"));

        assertThat(gateway.transcribe("file123")).isEmpty();
    }

    @Test
    @DisplayName("Deve transcrever quando Whisper retorna texto com espacos no final")
    void deveTranscreverComEspacosNoFinal() {
        Map<String, Object> fileResult = Map.of("file_path", "voice/file_0.oga");
        Map<String, Object> fileResponse = Map.of("ok", true, "result", fileResult);
        when(restTemplate.getForEntity(contains("getFile"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(fileResponse));
        when(restTemplate.getForObject(contains("file/bot"), eq(byte[].class)))
                .thenReturn(new byte[]{1, 2, 3, 4});
        when(restTemplate.postForEntity(contains("audio/transcriptions"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("quero treino   "));

        Optional<String> result = gateway.transcribe("file123", "audio/ogg");
        assertThat(result).contains("quero treino");
    }
}
