package br.edu.puc.fitjourneyai.core.flow.progress;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.*;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import br.edu.puc.fitjourneyai.infrastructure.chart.ProgressChartService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressFlowHandlerTest {

    @Mock private MeasurementRepository measurementRepository;
    @Mock private WorkoutRepository workoutRepository;
    @Mock private ProgressChartService chartService;
    @Mock private MessageGateway messageGateway;

    @InjectMocks
    private ProgressFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).telegramChatId(12345L).nome("Igor")
                .objetivo(GoalType.EMAGRECER).nivel(LevelType.INTERMEDIARIO)
                .frequenciaTreinoEstimada(4).onboardingConcluido(true)
                .build();

        state = ConversationState.builder()
                .id(1L).user(user).currentFlow(ConversationFlowType.NONE)
                .partialData("{}").updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve retornar PROGRESS como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.PROGRESS);
    }

    @Test
    @DisplayName("Deve bloquear se onboarding não concluído")
    void deveBloquearSemOnboarding() {
        user.setOnboardingConcluido(false);
        FlowResult result = handler.handle(ctx());
        assertThat(result.responseText()).contains("/start");
    }

    @Test
    @DisplayName("Deve informar quando não há dados suficientes")
    void deveInformarSemDados() {
        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowResult result = handler.handle(ctx());

        assertThat(result.responseText()).contains("não tenho dados suficientes");
        verify(messageGateway, never()).sendPhotoAlbum(any(), any(), any());
    }

    @Test
    @DisplayName("Deve gerar gráficos e enviar álbum quando há dados de peso e treinos")
    void deveGerarGraficosComDados() {
        // Peso data
        List<Measurement> pesoData = List.of(
                buildMeasurement(MeasurementType.PESO, 75.0, 20),
                buildMeasurement(MeasurementType.PESO, 74.5, 15),
                buildMeasurement(MeasurementType.PESO, 74.0, 10),
                buildMeasurement(MeasurementType.PESO, 73.5, 5),
                buildMeasurement(MeasurementType.PESO, 73.0, 1)
        );

        // Treinos
        List<Workout> treinos = List.of(
                buildWorkout(WorkoutGroup.PEITO, 45, 7, 20),
                buildWorkout(WorkoutGroup.COSTAS, 50, 8, 18),
                buildWorkout(WorkoutGroup.PERNAS, 60, 9, 15),
                buildWorkout(WorkoutGroup.PEITO, 45, 7, 10),
                buildWorkout(WorkoutGroup.OMBRO, 40, 6, 7),
                buildWorkout(WorkoutGroup.PERNAS, 55, 8, 3)
        );

        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(pesoData);
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(treinos);

        // Mocks dos gráficos
        byte[] fakeChart = new byte[]{1, 2, 3};
        when(chartService.generateWeightChart(any(), any())).thenReturn(fakeChart);
        when(chartService.generateTrainingFrequencyChart(any(), any())).thenReturn(fakeChart);
        when(chartService.generateMuscleGroupChart(any(), any())).thenReturn(fakeChart);

        FlowResult result = handler.handle(ctx());

        // Verifica envio do álbum
        verify(messageGateway).sendPhotoAlbum(eq(12345L), any(), any());

        // Verifica texto de análise
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("PESO");
        assertThat(result.responseText()).contains("73,0 kg");
        assertThat(result.responseText()).contains("TREINOS");
        assertThat(result.responseText()).contains("6 treinos");
    }

    @Test
    @DisplayName("Deve mostrar análise de variação de peso coerente com objetivo")
    void deveMostrarAnaliseCoerente() {
        List<Measurement> pesoData = List.of(
                buildMeasurement(MeasurementType.PESO, 75.0, 20),
                buildMeasurement(MeasurementType.PESO, 74.0, 1)
        );

        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(pesoData);
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(chartService.generateWeightChart(any(), any())).thenReturn(new byte[]{1});

        FlowResult result = handler.handle(ctx());

        // Objetivo é emagrecer e peso caiu → mensagem positiva
        assertThat(result.responseText()).contains("caindo");
    }

    @Test
    @DisplayName("Deve funcionar com apenas treinos (sem peso)")
    void deveFuncionarSoComTreinos() {
        when(measurementRepository.findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<Workout> treinos = List.of(
                buildWorkout(WorkoutGroup.PEITO, 45, 7, 5),
                buildWorkout(WorkoutGroup.COSTAS, 50, 8, 3)
        );
        when(workoutRepository.findByUserAndDataRealizacaoBetween(any(), any(), any()))
                .thenReturn(treinos);
        when(chartService.generateTrainingFrequencyChart(any(), any())).thenReturn(new byte[]{1});

        FlowResult result = handler.handle(ctx());

        assertThat(result.responseText()).contains("TREINOS");
        assertThat(result.responseText()).contains("2 treinos");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FlowContext ctx() {
        return new FlowContext(12345L, user, state, "/progresso",
                IntentType.PROGRESSO, LocalDateTime.now());
    }

    private Measurement buildMeasurement(MeasurementType tipo, double valor, int daysAgo) {
        return Measurement.builder()
                .user(user).tipo(tipo).valor(valor)
                .dataRegistro(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    private Workout buildWorkout(WorkoutGroup grupo, int duracao, int intensidade, int daysAgo) {
        return Workout.builder()
                .user(user).grupoMuscular(grupo).fonte(WorkoutSource.MANUAL)
                .duracaoMinutos(duracao).intensidadePercebida(intensidade)
                .dataRealizacao(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }
}
