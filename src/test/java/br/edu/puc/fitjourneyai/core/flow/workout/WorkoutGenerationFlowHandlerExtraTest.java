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
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutGroup;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import br.edu.puc.fitjourneyai.infrastructure.ai.OpenAiServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkoutGenerationFlowHandler — cobertura adicional")
class WorkoutGenerationFlowHandlerExtraTest {

    @Mock private OpenAiServiceImpl aiService;
    @Mock private WorkoutRepository workoutRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkoutGenerationFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).telegramChatId(12345L).nome("Igor")
                .objetivo(GoalType.GANHAR_MUSCULO).nivel(LevelType.INTERMEDIARIO)
                .frequenciaTreinoEstimada(4).onboardingConcluido(true).build();

        state = ConversationState.builder().id(1L).user(user)
                .currentFlow(ConversationFlowType.NONE)
                .partialData("{}").updatedAt(LocalDateTime.now()).build();
    }

    // ─── Enriquecimento com links de vídeo ────────────────────────────────

    @Test
    @DisplayName("Deve adicionar link de vídeo para linha numerada")
    void deveAdicionarVideoParaLinhaNumerada() {
        String treino = "1. Supino Reto\n2. Leg Press\n3. Beba água";
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn(treino);
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("peito"));
        assertThat(result.responseText()).contains("youtube.com");
        assertThat(result.responseText()).contains("Supino Reto");
    }

    @Test
    @DisplayName("Deve adicionar link de vídeo para linha com bullet maiúsculo")
    void deveAdicionarVideoParaLinhaBullet() {
        String treino = "- Agachamento Livre\n- Leg Press";
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn(treino);
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("pernas"));
        assertThat(result.responseText()).contains("youtube.com");
    }

    @Test
    @DisplayName("Deve adicionar link para cada exercício principal numerado com ')' ")
    void deveAdicionarLinkParaCadaExercicioPrincipalComParenteses() {
        String treino = """
                Exercícios Principais
                1) Supino reto com barra
                - 5 x 5 repetições
                2) Crossover no cabo
                - 3 x 15 repetições
                """;

        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn(treino);
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("peito"));

        assertThat(countOccurrences(result.responseText(), "🎥 Vídeo:"))
                .isEqualTo(2);
        assertThat(result.responseText()).contains("search_query=Supino+reto+com+barra");
        assertThat(result.responseText()).contains("search_query=Crossover+no+cabo");
    }

    @Test
    @DisplayName("Não deve adicionar link para verbo de dica (ex: 'Beba água')")
    void naoDeveAdicionarVideoParaDica() {
        String treino = "1. Beba água antes de treinar";
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn(treino);
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("peito"));
        // "Beba" começa com verbo dica → não gera link de vídeo
        assertThat(result.responseText()).doesNotContain("youtube.com");
    }

    @Test
    @DisplayName("Não deve adicionar link para nome muito curto (<4 chars)")
    void naoDeveAdicionarVideoParaNomeCurto() {
        String treino = "1. Abc";
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn(treino);
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("peito"));
        assertThat(result.responseText()).doesNotContain("youtube.com");
    }

    // ─── Histórico recente ────────────────────────────────────────────────

    @Test
    @DisplayName("Deve incluir últimos treinos na mensagem quando há histórico")
    void deveIncluirUltimosTreinos() {
        Workout w = Workout.builder()
                .grupoMuscular(WorkoutGroup.PEITO)
                .duracaoMinutos(50)
                .dataRealizacao(LocalDateTime.now().minusDays(1))
                .build();
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(List.of(w));

        FlowResult result = handler.handle(ctx("/treino"));
        assertThat(result.responseText()).contains("Histórico recente");
        assertThat(result.responseText()).contains("Peito");
        assertThat(result.responseText()).contains("50min");
    }

    @Test
    @DisplayName("Não deve despejar descrição completa de treino IA no /treino")
    void naoDeveDespejarDescricaoCompletaNoPedidoDeTreino() {
        Workout w = Workout.builder()
                .grupoMuscular(WorkoutGroup.PERNAS)
                .descricaoTreino("""
                        Treino: Pernas completas
                        Objetivo: Ganho de massa muscular
                        Aquecimento
                        1) Bicicleta ergométrica
                        Treino Principal
                        2) Agachamento livre
                        """)
                .duracaoMinutos(80)
                .dataRealizacao(LocalDateTime.now().minusDays(1))
                .build();
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(List.of(w));

        FlowResult result = handler.handle(ctx("/treino"));

        assertThat(result.responseText()).contains("Histórico recente");
        assertThat(result.responseText()).contains("Pernas");
        assertThat(result.responseText()).doesNotContain("Aquecimento");
        assertThat(result.responseText()).doesNotContain("Agachamento livre");
    }

    @Test
    @DisplayName("Deve mostrar mensagem de usuário sem histórico")
    void deveIndicarSemHistorico() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowResult result = handler.handle(ctx("/treino"));
        assertThat(result.nextStep()).isEqualTo(1);
    }

    // ─── Normalização de typos ────────────────────────────────────────────

    @Test
    @DisplayName("Typo 'perma' deve ser normalizado para 'pernas' (fuzzyMatch determinístico)")
    void deveTratarTypoPerma() {
        state.setCurrentFlow(ConversationFlowType.WORKOUT_GENERATION);
        state.setCurrentStep(1);
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn("Treino de pernas");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // "perma" é corrigido deterministicamente para "pernas" sem chamar IA
        FlowResult result = handler.handle(ctxActive("perma"));
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
    }

    @Test
    @DisplayName("Typo 'pito' deve ser normalizado para 'peito'")
    void deveTratarTypoPito() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn("Treino de peito");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx("pito"));
        assertThat(result.responseText()).contains("Treino de peito");
    }

    // ─── Persistência com falha silenciosa ───────────────────────────────

    @Test
    @DisplayName("Falha ao salvar treino não deve derrubar o fluxo")
    void falhaAoSalvarNaoDerruba() {
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn("Treino gerado");
        when(workoutRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        FlowResult result = handler.handle(ctx("costas"));
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("Treino gerado");
    }

    // ─── Step 2 (GENERATING) também gera treino ───────────────────────────

    @Test
    @DisplayName("Step GENERATING deve gerar treino normalmente")
    void stepGerandoDeveGerarTreino() {
        state.setCurrentFlow(ConversationFlowType.WORKOUT_GENERATION);
        state.setCurrentStep(2);
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateWorkout(any(), anyMap())).thenReturn("Treino de ombro");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctxActive("ombro"));
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────

    private FlowContext ctx(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.TREINO, LocalDateTime.now());
    }

    private FlowContext ctxActive(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.TREINO, LocalDateTime.now());
    }

    private int countOccurrences(String text, String token) {
        if (text == null || token == null || token.isEmpty()) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}

