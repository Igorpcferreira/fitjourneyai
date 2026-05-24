package br.edu.puc.fitjourneyai.adapter.telegram;

import br.edu.puc.fitjourneyai.adapter.telegram.dto.TelegramSendMessageRequest;
import br.edu.puc.fitjourneyai.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramGatewayExtraTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramProperties properties;

    private TelegramGateway gateway;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://api.telegram.org");
        lenient().when(properties.getBotToken()).thenReturn("token");
        gateway = new TelegramGateway(restTemplate, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("sendHtmlText nao envia quando null ou blank")
    void sendHtmlTextNaoEnviaNullOuBlank() {
        gateway.sendHtmlText(1L, null);
        gateway.sendHtmlText(1L, "   ");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendText converte markdown heading e bold para HTML")
    void sendTextConverteMarkdown() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendText(1L, "## Titulo\n**forte**");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("sendMessage"), captor.capture(), eq(String.class));

        TelegramSendMessageRequest payload = (TelegramSendMessageRequest) captor.getValue().getBody();
        assertThat(payload.getParseMode()).isEqualTo("HTML");
        assertThat(payload.getText()).contains("<b>Titulo</b>");
        assertThat(payload.getText()).contains("<b>forte</b>");
    }

    @Test
    @DisplayName("sendText com html pronto remove markdown residual")
    void sendTextComHtmlPronto() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendText(1L, "<b>ok</b>\n## subtitulo");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("sendMessage"), captor.capture(), eq(String.class));

        TelegramSendMessageRequest payload = (TelegramSendMessageRequest) captor.getValue().getBody();
        assertThat(payload.getText()).doesNotContain("##");
        assertThat(payload.getText()).contains("subtitulo");
    }

    @Test
    @DisplayName("sendPhoto sem caption nao adiciona caption nem parse_mode")
    void sendPhotoSemCaption() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendPhoto(1L, new byte[]{1, 2, 3}, "   ");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("sendPhoto"), captor.capture(), eq(String.class));

        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>) captor.getValue().getBody();
        assertThat(body.getFirst("caption")).isNull();
        assertThat(body.getFirst("parse_mode")).isNull();
    }

    @Test
    @DisplayName("sendPhoto captura excecao sem propagar")
    void sendPhotoErroNaoPropaga() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("down"));

        assertThatNoException().isThrownBy(() -> gateway.sendPhoto(1L, new byte[]{1}, "cap"));
    }

    @Test
    @DisplayName("sendPhotoAlbum ignora null e lista vazia")
    void sendPhotoAlbumIgnoraNullEVazio() {
        gateway.sendPhotoAlbum(1L, null, "cap");
        gateway.sendPhotoAlbum(1L, List.of(), "cap");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendPhotoAlbum faz fallback para sendPhoto quando sendMediaGroup falha")
    void sendPhotoAlbumFallbackParaFotosIndividuais() {
        when(restTemplate.postForEntity(contains("sendMediaGroup"), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("group down"));
        when(restTemplate.postForEntity(contains("sendPhoto"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        gateway.sendPhotoAlbum(1L, List.of(new byte[]{1}, new byte[]{2}), "Album");

        verify(restTemplate, times(1)).postForEntity(contains("sendMediaGroup"), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(2)).postForEntity(contains("sendPhoto"), any(HttpEntity.class), eq(String.class));
    }
}


