package br.edu.puc.fitjourneyai.adapter.telegram;

import br.edu.puc.fitjourneyai.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramGatewayTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramProperties properties;
    private TelegramGateway gateway;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://api.telegram.org");
        lenient().when(properties.getBotToken()).thenReturn("test-token");
        gateway = new TelegramGateway(restTemplate, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("Deve enviar mensagem curta em uma unica chamada")
    void deveEnviarMensagemCurta() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendText(12345L, "Ola!");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Deve dividir mensagem longa em multiplas partes")
    void deveDividirMensagemLonga() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        // Cria mensagem com > 4096 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Linha ").append(i).append(" do treino com exercicio detalhado.\n\n");
        }
        String longMsg = sb.toString(); // ~5000+ chars

        gateway.sendText(12345L, longMsg);
        verify(restTemplate, atLeast(2)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Nao deve enviar mensagem null")
    void naoDeveEnviarNull() {
        gateway.sendText(12345L, null);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Nao deve enviar mensagem vazia")
    void naoDeveEnviarVazia() {
        gateway.sendText(12345L, "   ");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Deve enviar HTML com split")
    void deveEnviarHtmlComSplit() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendHtmlText(12345L, "<b>Teste</b>");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Deve enviar foto com caption")
    void deveEnviarFoto() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendPhoto(12345L, new byte[]{1, 2, 3}, "Caption");
        verify(restTemplate, times(1)).postForEntity(contains("sendPhoto"), any(), eq(String.class));
    }

    @Test
    @DisplayName("Deve enviar album de fotos")
    void deveEnviarAlbum() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        List<byte[]> images = List.of(new byte[]{1}, new byte[]{2}, new byte[]{3});
        gateway.sendPhotoAlbum(12345L, images, "Album");
        verify(restTemplate, times(1)).postForEntity(contains("sendMediaGroup"), any(), eq(String.class));
    }

    @Test
    @DisplayName("Deve enviar foto unica quando album tem 1 imagem")
    void deveEnviarFotoUnicaParaAlbumDe1() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendPhotoAlbum(12345L, List.of(new byte[]{1}), "Uma foto");
        verify(restTemplate, times(1)).postForEntity(contains("sendPhoto"), any(), eq(String.class));
    }

    @Test
    @DisplayName("Deve lidar com erro de envio gracefully")
    void deveLidarComErroGracefully() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Telegram down"));

        // Nao deve lancar excecao
        gateway.sendText(12345L, "test");
    }
}
