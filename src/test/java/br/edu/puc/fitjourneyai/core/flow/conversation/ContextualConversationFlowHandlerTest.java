package br.edu.puc.fitjourneyai.core.flow.conversation;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.Message;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.MessageType;
import br.edu.puc.fitjourneyai.core.port.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextualConversationFlowHandlerTest {

    @Mock
    private AiService aiService;

    @Mock
    private MessageRepository messageRepository;

    private ContextualConversationFlowHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ContextualConversationFlowHandler(aiService, messageRepository);
    }

    @Test
    @DisplayName("Deve retornar flow type conversa contextual")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.CONTEXTUAL_CONVERSATION);
    }

    @Test
    @DisplayName("Deve orientar /start quando onboarding não concluído")
    void deveOrientarStartSemOnboarding() {
        User user = user(false, "Igor");

        FlowResult result = handler.handle(ctx(user, "oi"));

        assertThat(result.responseText()).contains("Manda um /start");
        assertThat(result.suggestedNextAction()).contains("/start");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve responder ajuda quando mensagem está vazia")
    void deveResponderQuandoMensagemVazia() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, "   "));

        assertThat(result.responseText()).contains("o que você quer saber");
        assertThat(result.suggestedNextAction()).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve responder ajuda quando mensagem é nula")
    void deveResponderQuandoMensagemNula() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, null));

        assertThat(result.responseText()).contains("o que você quer saber");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve responder exercício curado quando reconhecer no catálogo")
    void deveResponderExercicioCurado() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, "como fazer flexão?"));

        assertThat(result.responseText()).contains("Flexão de Braços");
        assertThat(result.responseText()).contains("Vídeo de referência");
        assertThat(result.suggestedNextAction()).contains("/treino");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve responder exercício dinâmico quando não estiver no catálogo")
    void deveResponderExercicioDinamico() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, "como fazer polichinelo?"));

        assertThat(result.responseText()).contains("Polichinelo");
        assertThat(result.responseText()).contains("Encontrei referências de como executar esse exercício");
        assertThat(result.responseText()).contains("Manda /treino");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve ir para IA quando extração de exercício não conseguir identificar termo")
    void deveIrParaIaQuandoExtracaoFalha() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("Resposta IA");

        FlowResult result = handler.handle(ctx(user, "fala sobre como fazer supino"));

        assertThat(result.responseText()).isEqualTo("Resposta IA");
        verify(aiService).composeContextualResponse(eq("fala sobre como fazer supino"), eq(user), eq("sem histórico"));
    }

    @Test
    @DisplayName("Deve ir para IA quando termo extraído for muito curto")
    void deveIrParaIaQuandoTermoCurto() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("Resposta IA curta");

        FlowResult result = handler.handle(ctx(user, "como fazer x"));

        assertThat(result.responseText()).isEqualTo("Resposta IA curta");
        verify(aiService).composeContextualResponse(eq("como fazer x"), eq(user), eq("sem histórico"));
    }

    @Test
    @DisplayName("Deve ir para IA quando termo extraído for muito longo")
    void deveIrParaIaQuandoTermoMuitoLongo() {
        User user = user(true, "Igor");
        String texto = "como fazer " + "a".repeat(70);
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("Resposta IA longa");

        FlowResult result = handler.handle(ctx(user, texto));

        assertThat(result.responseText()).isEqualTo("Resposta IA longa");
        verify(aiService).composeContextualResponse(eq(texto), eq(user), eq("sem histórico"));
    }

    @Test
    @DisplayName("Deve ir para IA quando termo extraído fica em branco")
    void deveIrParaIaQuandoTermoFicaEmBranco() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("Resposta IA branco");

        FlowResult result = handler.handle(ctx(user, "como fazer por favor"));

        assertThat(result.responseText()).isEqualTo("Resposta IA branco");
        verify(aiService).composeContextualResponse(eq("como fazer por favor"), eq(user), eq("sem histórico"));
    }

    @Test
    @DisplayName("Deve usar resposta da IA quando vier texto válido")
    void deveUsarRespostaDaIa() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse("oi", user, "sem histórico")).thenReturn("Fala, bora treinar?");

        FlowResult result = handler.handle(ctx(user, "oi"));

        assertThat(result.responseText()).isEqualTo("Fala, bora treinar?");
        assertThat(result.suggestedNextAction()).isNull();
    }

    @Test
    @DisplayName("Deve usar fallback com nome quando IA retorna em branco")
    void deveUsarFallbackQuandoIaRetornaEmBranco() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("   ");

        FlowResult result = handler.handle(ctx(user, "fala comigo"));

        assertThat(result.responseText()).contains("Igor, não consegui processar agora");
    }

    @Test
    @DisplayName("Deve usar fallback com nome quando IA retorna null")
    void deveUsarFallbackQuandoIaRetornaNull() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn(null);

        FlowResult result = handler.handle(ctx(user, "fala comigo"));

        assertThat(result.responseText()).contains("Igor, não consegui processar agora");
    }

    @Test
    @DisplayName("Deve encontrar exercício curado por chave contendo termo normalizado")
    void deveEncontrarCuradoPorChaveContendoTermo() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, "como fazer supino"));

        assertThat(result.responseText()).contains("Supino Reto");
        assertThat(result.responseText()).contains("Músculos:");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve entender 'como faço supino' como pedido de técnica")
    void deveEntenderComoFacoSupino() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, "Como faço supino?"));

        assertThat(result.responseText()).contains("Supino Reto");
        assertThat(result.responseText()).contains("youtube.com");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Pergunta sobre correr 5km deve retornar vídeo e dica, não treino completo")
    void deveResponderCorrer5KmComVideoEDica() {
        User user = user(true, "Igor");

        FlowResult result = handler.handle(ctx(user, "Como conseguir correr 5km?"));

        assertThat(result.responseText()).contains("5 km");
        assertThat(result.responseText()).contains("youtube.com");
        assertThat(result.responseText()).contains("Dica");
        assertThat(result.responseText()).contains("me monta um treino");
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve usar fallback padrão quando IA lança exceção e usuário sem nome")
    void deveUsarFallbackComAmigoQuandoIaLancaExcecao() {
        User user = user(true, null);
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenThrow(new RuntimeException("db down"));
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenThrow(new RuntimeException("ai down"));

        FlowResult result = handler.handle(ctx(user, "oi"));

        assertThat(result.responseText()).contains("amigo, não consegui processar agora");
        verify(aiService).composeContextualResponse(eq("oi"), eq(user), eq("sem histórico"));
    }

    @Test
    @DisplayName("Deve montar histórico com truncamento para IA")
    void deveMontarHistoricoComTruncamento() {
        User user = user(true, "Igor");
        String longText = "x".repeat(120);

        Message m1 = Message.builder().tipo(MessageType.USER).conteudo(longText).build();
        Message m2 = Message.builder().tipo(MessageType.BOT).conteudo(null).build();
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of(m1, m2));
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("ok");

        handler.handle(ctx(user, "oi"));

        ArgumentCaptor<String> historyCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).composeContextualResponse(eq("oi"), eq(user), historyCaptor.capture());
        String history = historyCaptor.getValue();

        assertThat(history).contains("USER: ");
        assertThat(history).contains("...");
        assertThat(history).contains("BOT: ");
    }

    @Test
    @DisplayName("Deve montar histórico com mensagem curta sem truncar")
    void deveMontarHistoricoSemTruncarMensagemCurta() {
        User user = user(true, "Igor");
        Message m1 = Message.builder().tipo(MessageType.USER).conteudo("curta").build();

        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of(m1));
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("ok");

        handler.handle(ctx(user, "oi"));

        ArgumentCaptor<String> historyCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).composeContextualResponse(eq("oi"), eq(user), historyCaptor.capture());
        assertThat(historyCaptor.getValue()).contains("USER: curta");
    }

    @Test
    @DisplayName("Deve manter conversa com IA quando não for intenção de exercício")
    void deveManterConversaQuandoNaoForExercicio() {
        User user = user(true, "Igor");
        when(messageRepository.findTop10ByUserOrderByDataHoraDesc(user)).thenReturn(List.of());
        when(aiService.composeContextualResponse(anyString(), eq(user), anyString())).thenReturn("resposta livre");

        FlowResult result = handler.handle(ctx(user, "qual o melhor horário para treinar?"));

        assertThat(result.responseText()).isEqualTo("resposta livre");
    }

    private FlowContext ctx(User user, String text) {
        ConversationState state = ConversationState.builder()
                .id(1L)
                .user(user)
                .currentFlow(ConversationFlowType.NONE)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();

        return new FlowContext(
                12345L,
                user,
                state,
                text,
                IntentType.CONVERSA,
                LocalDateTime.now()
        );
    }

    private User user(boolean onboardingConcluido, String nome) {
        return User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .nome(nome)
                .onboardingConcluido(onboardingConcluido)
                .build();
    }
}
