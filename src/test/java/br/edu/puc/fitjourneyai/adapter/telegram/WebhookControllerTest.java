package br.edu.puc.fitjourneyai.adapter.telegram;

import br.edu.puc.fitjourneyai.adapter.openai.WhisperGateway;
import br.edu.puc.fitjourneyai.adapter.telegram.dto.TelegramUpdate;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.orchestrator.ConversationOrchestrator;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock private ConversationOrchestrator orchestrator;
    @Mock private MessageGateway messageGateway;
    @Mock private WhisperGateway whisperGateway;
    @InjectMocks private WebhookController controller;

    @Test
    @DisplayName("Deve retornar 200 para update sem mensagem")
    void deveRetornar200SemMensagem() {
        TelegramUpdate update = new TelegramUpdate();
        ResponseEntity<Void> response = controller.onUpdate(update);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("Deve processar mensagem de texto e enviar resposta")
    void deveProcessarMensagemTexto() {
        TelegramUpdate update = buildUpdate(12345L, "oi");
        FlowResult result = FlowResult.done("Oi!", null);
        when(orchestrator.handleMessage(12345L, "oi")).thenReturn(result);

        ResponseEntity<Void> response = controller.onUpdate(update);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(messageGateway).sendText(12345L, "Oi!");
    }

    @Test
    @DisplayName("Deve retornar 200 quando orchestrator retorna null")
    void deveRetornar200QuandoResultNull() {
        TelegramUpdate update = buildUpdate(12345L, "algo");
        when(orchestrator.handleMessage(12345L, "algo")).thenReturn(null);

        ResponseEntity<Void> response = controller.onUpdate(update);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(messageGateway);
    }

    @Test
    @DisplayName("Deve enviar foto quando resultado tem imagem")
    void deveEnviarFotoComImagem() {
        TelegramUpdate update = buildUpdate(12345L, "/progresso");
        byte[] img = new byte[]{1, 2, 3};
        FlowResult result = FlowResult.withImage("Analise", img, "Caption", null);
        when(orchestrator.handleMessage(12345L, "/progresso")).thenReturn(result);

        controller.onUpdate(update);

        verify(messageGateway).sendPhoto(eq(12345L), eq(img), eq("Caption"));
        verify(messageGateway).sendText(eq(12345L), eq("Analise"));
    }

    @Test
    @DisplayName("Deve enviar fallback quando ocorre excecao")
    void deveEnviarFallbackEmExcecao() {
        TelegramUpdate update = buildUpdate(12345L, "crash");
        when(orchestrator.handleMessage(12345L, "crash")).thenThrow(new RuntimeException("Erro"));

        controller.onUpdate(update);

        verify(messageGateway).sendText(eq(12345L), contains("problema"));
    }

    @Test
    @DisplayName("Deve ignorar update com chat null")
    void deveIgnorarChatNull() {
        TelegramUpdate update = new TelegramUpdate();
        TelegramUpdate.TelegramMessage msg = new TelegramUpdate.TelegramMessage();
        msg.setText("oi");
        update.setMessage(msg);

        ResponseEntity<Void> response = controller.onUpdate(update);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("Deve transcrever voice e processar texto transcrito")
    void deveProcessarVoiceComTranscricao() {
        TelegramUpdate update = buildVoiceUpdate(12345L, "voice-file-id", "audio/ogg");
        when(whisperGateway.transcribe("voice-file-id", "audio/ogg")).thenReturn(Optional.of("quero treino"));
        when(orchestrator.handleMessage(12345L, "quero treino")).thenReturn(FlowResult.done("ok", null));

        ResponseEntity<Void> response = controller.onUpdate(update);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(orchestrator).handleMessage(12345L, "quero treino");
        verify(messageGateway).sendText(12345L, "ok");
    }

    @Test
    @DisplayName("Deve enviar fallback quando voice nao for transcrito")
    void deveEnviarFallbackQuandoVoiceNaoTranscrito() {
        TelegramUpdate update = buildVoiceUpdate(12345L, "voice-file-id", "audio/ogg");
        when(whisperGateway.transcribe("voice-file-id", "audio/ogg")).thenReturn(Optional.empty());

        ResponseEntity<Void> response = controller.onUpdate(update);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(messageGateway).sendText(eq(12345L), contains("Não consegui entender o áudio"));
        verifyNoInteractions(orchestrator);
    }

    private TelegramUpdate buildUpdate(Long chatId, String text) {
        TelegramUpdate update = new TelegramUpdate();
        TelegramUpdate.TelegramMessage msg = new TelegramUpdate.TelegramMessage();
        TelegramUpdate.TelegramChat chat = new TelegramUpdate.TelegramChat();
        chat.setId(chatId);
        msg.setChat(chat);
        msg.setText(text);
        update.setMessage(msg);
        return update;
    }

    private TelegramUpdate buildVoiceUpdate(Long chatId, String fileId, String mimeType) {
        TelegramUpdate update = new TelegramUpdate();
        TelegramUpdate.TelegramMessage msg = new TelegramUpdate.TelegramMessage();
        TelegramUpdate.TelegramChat chat = new TelegramUpdate.TelegramChat();
        TelegramUpdate.TelegramVoice voice = new TelegramUpdate.TelegramVoice();

        chat.setId(chatId);
        voice.setFileId(fileId);
        voice.setMimeType(mimeType);

        msg.setChat(chat);
        msg.setVoice(voice);
        update.setMessage(msg);
        return update;
    }
}
