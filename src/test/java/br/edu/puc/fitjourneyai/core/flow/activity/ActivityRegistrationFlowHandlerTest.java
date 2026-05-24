package br.edu.puc.fitjourneyai.core.flow.activity;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutGroup;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutSource;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityRegistrationFlowHandlerTest {

    @Mock private WorkoutRepository workoutRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ActivityRegistrationFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .onboardingConcluido(true)
                .build();

        state = ConversationState.builder()
                .id(1L)
                .user(user)
                .currentFlow(ConversationFlowType.ACTIVITY_REGISTRATION)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve retornar ACTIVITY_REGISTRATION como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.ACTIVITY_REGISTRATION);
    }

    @Test
    @DisplayName("Deve bloquear se onboarding não concluído")
    void deveBloquearSemOnboarding() {
        user.setOnboardingConcluido(false);
        FlowResult result = handler.handle(ctx(null, "/treino_feito"));
        assertThat(result.responseText()).contains("/start");
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
    }

    @Test
    @DisplayName("Deve iniciar pedindo grupo muscular")
    void deveIniciarPedindoGrupo() {
        state.setCurrentStep(null);
        state.setCurrentFlow(ConversationFlowType.NONE);

        FlowResult result = handler.handle(ctx(null, "/treino_feito"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.ACTIVITY_REGISTRATION);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("tipo de treino");
    }

    @Test
    @DisplayName("Deve perguntar se treino feito foi o último sugerido pela IA")
    void devePerguntarSeFoiUltimoTreinoSugerido() {
        state.setCurrentStep(null);
        state.setCurrentFlow(ConversationFlowType.NONE);
        Workout suggested = Workout.builder()
                .id(99L)
                .user(user)
                .fonte(WorkoutSource.IA)
                .grupoMuscular(WorkoutGroup.PEITO)
                .descricaoTreino("Treino: Peito + Tríceps + Ombro\nDuração estimada: 70-90 min")
                .dataGeracao(LocalDateTime.now())
                .observacoes("Pedido: peito, tríceps e ombro")
                .build();
        when(workoutRepository.findTopByUserAndFonteAndDataRealizacaoIsNullAndDataGeracaoAfterOrderByDataGeracaoDesc(
                any(), eq(WorkoutSource.IA), any())).thenReturn(Optional.of(suggested));

        FlowResult result = handler.handle(ctx(null, "/treino_feito"));

        assertThat(result.nextStep()).isEqualTo(6);
        assertThat(result.responseText()).contains("treino que eu te sugeri");
        assertThat(result.stateData()).containsEntry("suggestedWorkoutId", "99");
    }

    @Test
    @DisplayName("Deve normalizar pedido bruto do treino sugerido")
    void deveNormalizarPedidoBrutoDoTreinoSugerido() {
        state.setCurrentStep(null);
        state.setCurrentFlow(ConversationFlowType.NONE);
        Workout suggested = Workout.builder()
                .id(99L)
                .user(user)
                .fonte(WorkoutSource.IA)
                .grupoMuscular(WorkoutGroup.PEITO)
                .descricaoTreino("Treino: Peito + Ombro + Tríceps\nDuração estimada: 70-90 min")
                .dataGeracao(LocalDateTime.now())
                .observacoes("Pedido: Me manda um treinão de peito, ombro e tríceps")
                .build();
        when(workoutRepository.findTopByUserAndFonteAndDataRealizacaoIsNullAndDataGeracaoAfterOrderByDataGeracaoDesc(
                any(), eq(WorkoutSource.IA), any())).thenReturn(Optional.of(suggested));

        FlowResult result = handler.handle(ctx(null, "/treino_feito"));

        assertThat(result.responseText()).contains("Peito + Ombro + Tríceps");
        assertThat(result.stateData()).containsEntry("grupoTexto", "Peito + Ombro + Tríceps");
    }

    @Test
    @DisplayName("Confirmar treino sugerido deve registrar automaticamente")
    void confirmarTreinoSugeridoRegistraAutomaticamente() {
        Workout suggested = Workout.builder()
                .id(99L)
                .user(user)
                .fonte(WorkoutSource.IA)
                .grupoMuscular(WorkoutGroup.PEITO)
                .descricaoTreino("Treino: Peito + Tríceps + Ombro\nDuração estimada: 70-90 min\n1) Supino reto")
                .dataGeracao(LocalDateTime.now())
                .observacoes("Pedido: peito, tríceps e ombro")
                .build();
        state.setCurrentStep(6);
        state.setPartialData("{\"suggestedWorkoutId\":\"99\",\"grupoTexto\":\"peito, tríceps e ombro\",\"grupo\":\"PEITO\",\"duracao\":\"80\"}");
        when(workoutRepository.findById(99L)).thenReturn(Optional.of(suggested));
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx(6, "sim"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("registrado com sucesso");

        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(captor.capture());
        Workout saved = captor.getValue();
        assertThat(saved.getDataRealizacao()).isNotNull();
        assertThat(saved.getFonte()).isEqualTo(WorkoutSource.IA);
        assertThat(saved.getDuracaoMinutos()).isEqualTo(80);
        assertThat(saved.getObservacoes()).contains("Confirmado pelo usuário");
    }

    @Test
    @DisplayName("Step 1: Deve aceitar grupo muscular e avançar para duração")
    void deveAceitarGrupo() {
        FlowResult result = handler.handle(ctx(1, "peito e tríceps"));

        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(result.responseText()).contains("peito e tríceps");
        assertThat(result.responseText()).contains("minutos");
        assertThat(result.stateData()).containsEntry("grupo", "PEITO");
        assertThat(result.stateData()).containsEntry("grupoTexto", "peito e tríceps");
    }

    @Test
    @DisplayName("Step 1: Deve rejeitar texto vazio")
    void deveRejeitarGrupoVazio() {
        FlowResult result = handler.handle(ctx(1, "  "));

        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("Hmm, não entendi qual treino v");
    }

    @ParameterizedTest
    @CsvSource({
            "'peito e tríceps', PEITO",
            "'costas', COSTAS",
            "'pernas', PERNAS",
            "'ombro', OMBRO",
            "'bíceps e tríceps', BRACOS",
            "'abdominal', ABDOMEN",
            "'fullbody', FULLBODY",
            "'cardio hiit', CARDIO",
            "'corrida na esteira', CORRIDA",
            "'yoga', OUTRO"
    })
    @DisplayName("Deve mapear texto livre para WorkoutGroup correto")
    void deveMappearGrupoCorreto(String texto, String expectedGroup) {
        FlowResult result = handler.handle(ctx(1, texto));

        assertThat(result.stateData().get("grupo")).isEqualTo(expectedGroup);
    }

    @Test
    @DisplayName("Step 2: Deve aceitar duração válida e avançar para intensidade")
    void deveAceitarDuracao() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\"}");
        FlowResult result = handler.handle(ctx(2, "45"));

        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.responseText()).contains("45 minutos");
        assertThat(result.responseText()).contains("intensidade");
    }

    @Test
    @DisplayName("Step 2: Deve rejeitar duração fora de range")
    void deveRejeitarDuracaoInvalida() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\"}");
        FlowResult result = handler.handle(ctx(2, "500"));

        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(result.responseText()).contains("5 e 300");
    }

    @Test
    @DisplayName("Step 3: Deve aceitar intensidade e avançar para observações")
    void deveAceitarIntensidade() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\",\"duracao\":\"45\"}");
        FlowResult result = handler.handle(ctx(3, "7"));

        assertThat(result.nextStep()).isEqualTo(4);
        assertThat(result.responseText()).contains("7/10");
        assertThat(result.responseText()).contains("exercícios");
    }

    @Test
    @DisplayName("Step 3: Deve rejeitar intensidade fora de 1-10")
    void deveRejeitarIntensidadeInvalida() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\",\"duracao\":\"45\"}");
        FlowResult result = handler.handle(ctx(3, "15"));

        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.responseText()).contains("1 a 10");
    }

    @Test
    @DisplayName("Step 4: Deve aceitar observações e mostrar resumo")
    void deveAceitarObservacoesEMostrarResumo() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito e tríceps\",\"duracao\":\"45\",\"intensidade\":\"7\"}");
        FlowResult result = handler.handle(ctx(4, "Supino reto 4x12, crucifixo 3x15"));

        assertThat(result.nextStep()).isEqualTo(5);
        assertThat(result.responseText()).contains("Posso registrar");
        assertThat(result.responseText()).contains("peito e tríceps");
        assertThat(result.responseText()).contains("45 min");
        assertThat(result.responseText()).contains("7/10");
        assertThat(result.responseText()).contains("Supino reto");
    }

    @Test
    @DisplayName("Step 4: Deve aceitar 'pular' observações")
    void devePularObservacoes() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\",\"duracao\":\"45\",\"intensidade\":\"7\"}");
        FlowResult result = handler.handle(ctx(4, "pular"));

        assertThat(result.nextStep()).isEqualTo(5);
        assertThat(result.responseText()).contains("Posso registrar");
        assertThat(result.stateData()).doesNotContainKey("observacoes");
    }

    @Test
    @DisplayName("Step 5: Confirmar 'sim' deve persistir Workout no banco")
    void deveConfirmarESalvar() {
        state.setPartialData("{\"grupo\":\"PERNAS\",\"grupoTexto\":\"pernas\",\"duracao\":\"60\",\"intensidade\":\"8\",\"observacoes\":\"Agachamento livre\"}");

        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx(5, "sim"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("registrado com sucesso");
        assertThat(result.responseText()).contains("pernas");

        // Verifica persistência
        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(captor.capture());

        Workout saved = captor.getValue();
        assertThat(saved.getGrupoMuscular()).isEqualTo(WorkoutGroup.PERNAS);
        assertThat(saved.getFonte()).isEqualTo(WorkoutSource.MANUAL);
        assertThat(saved.getDuracaoMinutos()).isEqualTo(60);
        assertThat(saved.getIntensidadePercebida()).isEqualTo(8);
        assertThat(saved.getObservacoes()).isEqualTo("Agachamento livre");
        assertThat(saved.getDataRealizacao()).isNotNull();
    }

    @Test
    @DisplayName("Step 5: 'não' deve reiniciar no step 1")
    void deveReiniciarComNao() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\",\"duracao\":\"45\",\"intensidade\":\"7\"}");
        FlowResult result = handler.handle(ctx(5, "não"));

        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.stateData()).isEmpty();
    }

    @Test
    @DisplayName("Deve ter suggestedNextAction ao finalizar")
    void deveTerSugestedNextAction() {
        state.setPartialData("{\"grupo\":\"PEITO\",\"grupoTexto\":\"peito\",\"duracao\":\"45\",\"intensidade\":\"7\"}");
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FlowResult result = handler.handle(ctx(5, "sim"));

        assertThat(result.suggestedNextAction()).isNotNull();
        assertThat(result.suggestedNextAction()).contains("/progresso");
    }

    @Test
    @DisplayName("Fluxo completo end-to-end")
    void fluxoCompleto() {
        // Step 1: grupo
        FlowResult r1 = handler.handle(ctx(1, "corrida na esteira"));
        assertThat(r1.nextStep()).isEqualTo(2);
        assertThat(r1.stateData().get("grupo")).isEqualTo("CORRIDA");

        // Step 2: duração
        state.setPartialData("{\"grupo\":\"CORRIDA\",\"grupoTexto\":\"corrida na esteira\"}");
        FlowResult r2 = handler.handle(ctx(2, "30"));
        assertThat(r2.nextStep()).isEqualTo(3);

        // Step 3: intensidade
        state.setPartialData("{\"grupo\":\"CORRIDA\",\"grupoTexto\":\"corrida na esteira\",\"duracao\":\"30\"}");
        FlowResult r3 = handler.handle(ctx(3, "6"));
        assertThat(r3.nextStep()).isEqualTo(4);

        // Step 4: pular obs
        state.setPartialData("{\"grupo\":\"CORRIDA\",\"grupoTexto\":\"corrida na esteira\",\"duracao\":\"30\",\"intensidade\":\"6\"}");
        FlowResult r4 = handler.handle(ctx(4, "pular"));
        assertThat(r4.nextStep()).isEqualTo(5);

        // Step 5: confirmar
        when(workoutRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        FlowResult r5 = handler.handle(ctx(5, "sim"));
        assertThat(r5.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(r5.responseText()).contains("registrado com sucesso");

        verify(workoutRepository).save(any());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FlowContext ctx(Integer step, String text) {
        if (step != null) {
            state.setCurrentStep(step);
        }
        return new FlowContext(12345L, user, state, text, IntentType.TREINO_FEITO, LocalDateTime.now());
    }
}
