package br.edu.puc.fitjourneyai.core.flow.workout;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.GoalType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.LevelType;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutSource;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import br.edu.puc.fitjourneyai.infrastructure.ai.OpenAiServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutGenerationFlowHandlerTest {

    @Mock private OpenAiServiceImpl aiService;
    @Mock private WorkoutRepository workoutRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkoutGenerationFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .nome("Igor")
                .objetivo(GoalType.GANHAR_MUSCULO)
                .nivel(LevelType.INTERMEDIARIO)
                .frequenciaTreinoEstimada(4)
                .onboardingConcluido(true)
                .build();

        state = ConversationState.builder()
                .id(1L)
                .user(user)
                .currentFlow(ConversationFlowType.NONE)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve retornar WORKOUT_GENERATION como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.WORKOUT_GENERATION);
    }

    @Test
    @DisplayName("Deve bloquear se onboarding não concluído")
    void deveBloquearSemOnboarding() {
        user.setOnboardingConcluido(false);
        FlowResult result = handler.handle(ctx("/treino"));
        assertThat(result.responseText()).contains("/start");
    }

    @Test
    @DisplayName("Deve pedir o que treinar quando recebe só /treino")
    void devePedirOQueTreinar() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowResult result = handler.handle(ctx("/treino"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.WORKOUT_GENERATION);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("quer treinar");
    }

    @Test
    @DisplayName("Deve gerar treino direto quando texto contém pedido")
    void deveGerarDiretoComPedido() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap()))
                .thenReturn("Treino de pernas gerado pela IA com 5 exercícios");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("quero um treino de pernas"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("Treino de pernas gerado pela IA");
        assertThat(result.responseText()).contains("/treino_feito");
    }

    @Test
    @DisplayName("Deve gerar treino quando responde no step 1")
    void deveGerarNoStep1() {
        state.setCurrentFlow(ConversationFlowType.WORKOUT_GENERATION);
        state.setCurrentStep(1);

        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap()))
                .thenReturn("Treino de costas completo");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctxActive("costas"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("Treino de costas completo");
    }

    @Test
    @DisplayName("Deve persistir treino gerado com fonte=IA")
    void devePersistirComFonteIA() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap()))
                .thenReturn("Treino gerado");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        handler.handle(ctx("peito"));

        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(captor.capture());

        Workout saved = captor.getValue();
        assertThat(saved.getFonte()).isEqualTo(WorkoutSource.IA);
        assertThat(saved.getDataGeracao()).isNotNull();
        assertThat(saved.getDescricaoTreino()).isEqualTo("Treino gerado");
        assertThat(saved.getObservacoes()).contains("peito");
    }

    @Test
    @DisplayName("Deve usar fallback quando IA falha")
    void deveUsarFallbackQuandoIAFalha() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap()))
                .thenReturn("pernas (treino padrão)\n\nAquecimento"); // fallback retornado pelo AiService
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("pernas"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).isNotNull();
    }

    @Test
    @DisplayName("Deve extrair pedido de texto com /treino como prefixo")
    void deveExtrairPedidoComPrefixo() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap()))
                .thenReturn("Treino de ombro");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("/treino ombro e trapézio"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);

        // Verifica que o pedido passado ao AI contém "ombro e trapézio"
        ArgumentCaptor<Map<String, String>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiService).generateWorkout(any(), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue().get("pedido")).contains("ombro");
    }

    @Test
    @DisplayName("Deve ter suggestedNextAction ao finalizar")
    void deveTerSuggestedNextAction() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn("Treino gerado");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("peito"));

        assertThat(result.suggestedNextAction()).contains("/treino_feito");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FlowContext ctx(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.TREINO, LocalDateTime.now());
    }

    private FlowContext ctxActive(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.TREINO, LocalDateTime.now());
    }
}
