package br.edu.puc.fitjourneyai.infrastructure.ai;

import br.edu.puc.fitjourneyai.adapter.openai.dto.OpenAiDtos.ChatMessage;
import br.edu.puc.fitjourneyai.adapter.openai.OpenAiGateway;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.GoalType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.LevelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiServiceImplTest {

    @Mock private OpenAiGateway gateway;
    @InjectMocks private OpenAiServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).nome("Igor")
                .objetivo(GoalType.GANHAR_MUSCULO).nivel(LevelType.AVANCADO)
                .frequenciaTreinoEstimada(5).build();
    }

    @Test
    @DisplayName("classifyIntent deve retornar intent correta")
    void classifyIntentCorreto() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.of("TREINO"));
        IntentType result = service.classifyIntent("quero treinar", "contexto");
        assertThat(result).isEqualTo(IntentType.TREINO);
    }

    @Test
    @DisplayName("classifyIntent deve retornar UNKNOWN quando IA falha")
    void classifyIntentFallback() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.empty());
        assertThat(service.classifyIntent("algo", "ctx")).isEqualTo(IntentType.UNKNOWN);
    }

    @Test
    @DisplayName("classifyIntent deve retornar UNKNOWN para resposta invalida")
    void classifyIntentInvalido() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.of("INVALIDO"));
        assertThat(service.classifyIntent("algo", "ctx")).isEqualTo(IntentType.UNKNOWN);
    }

    @Test
    @DisplayName("generateWorkout deve retornar treino da IA")
    void generateWorkoutSucesso() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Optional.of("Treino de pernas:\n1. Agachamento 4x10"));
        String result = service.generateWorkout(user, Map.of("pedido", "pernas", "grupoMuscular", "pernas", "ultimosTreinos", "nenhum"));
        assertThat(result).contains("Agachamento");
    }

    @Test
    @DisplayName("generateWorkout deve usar fallback quando IA falha")
    void generateWorkoutFallback() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.empty());
        String result = service.generateWorkout(user, Map.of("pedido", "pernas", "grupoMuscular", "pernas", "ultimosTreinos", ""));
        assertThat(result).contains("treino");
    }

    @Test
    @DisplayName("generateMotivation deve retornar mensagem da IA")
    void generateMotivationSucesso() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Optional.of("Continue firme, Igor!"));
        String result = service.generateMotivation(user, Map.of("treinos", 5));
        assertThat(result).contains("Igor");
    }

    @Test
    @DisplayName("generateMotivation deve usar fallback")
    void generateMotivationFallback() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.empty());
        String result = service.generateMotivation(user, Map.of());
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("generateSummary deve retornar resumo da IA")
    void generateSummarySucesso() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Optional.of("Resumo da semana: excelente!"));
        String result = service.generateSummary(user, Map.of("totalTreinos", 5));
        assertThat(result).contains("excelente");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("generateSummary deve orientar IA sobre início de jornada")
    void generateSummaryPromptInicioJornada() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Optional.of("Resumo inicial."));

        service.generateSummary(user, Map.of("inicioJornada", true, "diasAcompanhados", 1));

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(gateway).chatCompletion(captor.capture(), eq(0.7), eq(500));
        assertThat(captor.getValue().get(0).getContent()).contains("inicioJornada=true");
        assertThat(captor.getValue().get(0).getContent()).contains("evite bronca");
    }

    @Test
    @DisplayName("composeContextualResponse deve retornar resposta da IA")
    void composeContextualSucesso() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Optional.of("O descanso ideal e de 60-90 segundos."));
        String result = service.composeContextualResponse("tempo de descanso?", user, "historico");
        assertThat(result).contains("descanso");
    }

    @Test
    @DisplayName("composeContextualResponse deve retornar null quando IA falha")
    void composeContextualFallback() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.empty());
        assertThat(service.composeContextualResponse("algo", user, "")).isNull();
    }

    @Test
    @DisplayName("composeNudgeMessage deve retornar nudge da IA")
    void composeNudgeSucesso() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Optional.of("Fala Igor, bora treinar!"));
        String result = service.composeNudgeMessage(user, 5);
        assertThat(result).contains("Igor");
    }

    @Test
    @DisplayName("normalizeWorkoutGroup deve corrigir typo")
    void normalizeWorkoutGroupSucesso() {
        when(gateway.chatCompletion(any(), anyDouble(), anyInt())).thenReturn(Optional.of("pernas"));
        String result = service.normalizeWorkoutGroup("perma");
        assertThat(result).isEqualTo("pernas");
    }
}
