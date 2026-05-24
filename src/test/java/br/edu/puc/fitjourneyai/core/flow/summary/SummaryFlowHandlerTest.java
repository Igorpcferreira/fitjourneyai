package br.edu.puc.fitjourneyai.core.flow.summary;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.*;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryFlowHandlerTest {

    @Mock private MeasurementRepository measurementRepository;
    @Mock private WorkoutRepository workoutRepository;
    @Mock private AiService aiService;

    @InjectMocks private SummaryFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).telegramChatId(12345L).nome("Igor")
                .objetivo(GoalType.GANHAR_MUSCULO).nivel(LevelType.INTERMEDIARIO)
                .frequenciaTreinoEstimada(4).onboardingConcluido(true)
                .build();
        state = ConversationState.builder()
                .id(1L).user(user).currentFlow(ConversationFlowType.NONE)
                .partialData("{}").updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve retornar SUMMARY como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.SUMMARY);
    }

    @Test
    @DisplayName("Deve informar sem dados quando não há registros")
    void deveInformarSemDados() {
        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowResult result = handler.handle(ctx("/resumo"));
        assertThat(result.responseText()).contains("não encontrei registros");
    }

    @Test
    @DisplayName("Deve gerar resumo semanal com dados de peso e treinos")
    void deveGerarResumoSemanal() {
        List<Measurement> pesoData = List.of(
                buildMeasurement(75.0, 6), buildMeasurement(74.5, 1));
        List<Workout> treinos = List.of(
                buildWorkout(WorkoutGroup.PEITO, 45, 7, 5),
                buildWorkout(WorkoutGroup.COSTAS, 50, 8, 3),
                buildWorkout(WorkoutGroup.PERNAS, 60, 9, 1));

        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(pesoData);
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(treinos);
        when(aiService.generateSummary(any(), anyMap()))
                .thenReturn("Ótimo progresso esta semana!");

        FlowResult result = handler.handle(ctx("/resumo"));

        assertThat(result.responseText()).contains("Resumo");
        assertThat(result.responseText()).contains("Peso");
        assertThat(result.responseText()).contains("Treinos: 3");
        assertThat(result.responseText()).contains("Ótimo progresso");
    }

    @Test
    @DisplayName("Deve gerar resumo mensal quando texto contém 'mensal'")
    void deveGerarResumoMensal() {
        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(List.of(buildMeasurement(75.0, 25), buildMeasurement(73.5, 1)));
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(List.of(buildWorkout(WorkoutGroup.PEITO, 45, 7, 5)));
        when(aiService.generateSummary(any(), anyMap())).thenReturn("Análise mensal.");

        FlowResult result = handler.handle(ctx("/resumo mensal"));
        assertThat(result.responseText()).contains("últimos 30 dias");
    }

    @Test
    @DisplayName("Deve tratar início recente como começo de jornada")
    void deveTratarInicioRecenteComoComecoDeJornada() {
        user.setCreatedAt(LocalDateTime.now());
        user.setFrequenciaTreinoEstimada(5);
        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(List.of(buildWorkout(WorkoutGroup.PEITO, 78, 8, 0)));
        when(aiService.generateSummary(any(), anyMap()))
                .thenReturn("Começo de acompanhamento, bora manter o ritmo.");

        FlowResult result = handler.handle(ctx("/resumo"));

        assertThat(result.responseText()).contains("primeiros 1 dia comigo");
        assertThat(result.responseText()).contains("Registrado até agora: 1x");
        assertThat(result.responseText()).doesNotContain("%");
        verify(aiService).generateSummary(eq(user), argThat(indicators ->
                Boolean.TRUE.equals(indicators.get("inicioJornada"))
                        && Integer.valueOf(1).equals(indicators.get("diasAcompanhados"))
                        && !indicators.containsKey("percentualMeta")));
    }

    @Test
    @DisplayName("Deve usar fallback quando IA falha")
    void deveUsarFallback() {
        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(List.of(buildMeasurement(75.0, 6), buildMeasurement(74.0, 1)));
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(List.of(buildWorkout(WorkoutGroup.PEITO, 45, 7, 3)));
        when(aiService.generateSummary(any(), anyMap())).thenThrow(new RuntimeException("API down"));

        FlowResult result = handler.handle(ctx("/resumo"));
        assertThat(result.responseText()).contains("Peso");
        // Should not throw, fallback is used
    }

    private FlowContext ctx(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.RESUMO, LocalDateTime.now());
    }

    private Measurement buildMeasurement(double valor, int daysAgo) {
        return Measurement.builder().user(user).tipo(MeasurementType.PESO).valor(valor)
                .dataRegistro(LocalDateTime.now().minusDays(daysAgo)).build();
    }

    private Workout buildWorkout(WorkoutGroup grupo, int duracao, int intensidade, int daysAgo) {
        return Workout.builder().user(user).grupoMuscular(grupo).fonte(WorkoutSource.MANUAL)
                .descricaoTreino(grupo.name().toLowerCase()).duracaoMinutos(duracao)
                .intensidadePercebida(intensidade).dataRealizacao(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }
}
