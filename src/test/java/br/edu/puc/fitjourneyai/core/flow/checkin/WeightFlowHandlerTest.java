package br.edu.puc.fitjourneyai.core.flow.checkin;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.MeasurementType;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeightFlowHandlerTest {

    @Mock private MeasurementRepository measurementRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private WeightFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .onboardingConcluido(true)
                .pesoAtual(72.0)
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
    @DisplayName("Deve retornar WEIGHT_CHECKIN como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.WEIGHT_CHECKIN);
    }

    @Test
    @DisplayName("Deve bloquear se onboarding não concluído")
    void deveBloquearSemOnboarding() {
        user.setOnboardingConcluido(false);
        FlowResult result = handler.handle(ctx("/peso"));
        assertThat(result.responseText()).contains("/start");
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
    }

    @Test
    @DisplayName("Deve pedir peso quando recebe /peso sem valor")
    void devePedirPesoComComando() {
        FlowResult result = handler.handle(ctx("/peso"));
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.WEIGHT_CHECKIN);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("peso em kg");
    }

    @Test
    @DisplayName("Deve registrar peso diretamente quando recebe número isolado")
    void deveRegistrarDireto() {
        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), eq(MeasurementType.PESO)))
                .thenReturn(Optional.empty());
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        FlowResult result = handler.handle(ctx("72.5"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("72,5 kg");
        assertThat(result.responseText()).contains("primeiro registro");

        // Verifica que persistiu como Measurement
        ArgumentCaptor<Measurement> captor = ArgumentCaptor.forClass(Measurement.class);
        verify(measurementRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(MeasurementType.PESO);
        assertThat(captor.getValue().getValor()).isEqualTo(72.5);
    }

    @Test
    @DisplayName("Deve mostrar diff quando há registro anterior")
    void deveMostrarDiffComAnterior() {
        Measurement anterior = Measurement.builder()
                .valor(74.0).tipo(MeasurementType.PESO).build();
        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), eq(MeasurementType.PESO)))
                .thenReturn(Optional.of(anterior));
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        FlowResult result = handler.handle(ctx("72"));

        assertThat(result.responseText()).contains("Peso registrado");
    }

    @Test
    @DisplayName("Deve mostrar aumento quando peso subiu")
    void deveMostrarAumento() {
        Measurement anterior = Measurement.builder()
                .valor(70.0).tipo(MeasurementType.PESO).build();
        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), eq(MeasurementType.PESO)))
                .thenReturn(Optional.of(anterior));
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        FlowResult result = handler.handle(ctx("72"));

        assertThat(result.responseText()).contains("Peso registrado");
    }

    @Test
    @DisplayName("Deve aceitar peso com vírgula no step de espera")
    void deveAceitarPesoComVirgulaNoStep() {
        state.setCurrentFlow(ConversationFlowType.WEIGHT_CHECKIN);
        state.setCurrentStep(1);

        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), eq(MeasurementType.PESO)))
                .thenReturn(Optional.empty());
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        FlowResult result = handler.handle(ctxWithActiveFlow("72,5"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("72,5 kg");
    }

    @Test
    @DisplayName("Deve rejeitar peso inválido no step de espera")
    void deveRejeitarPesoInvalidoNoStep() {
        state.setCurrentFlow(ConversationFlowType.WEIGHT_CHECKIN);
        state.setCurrentStep(1);

        FlowResult result = handler.handle(ctxWithActiveFlow("abc"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.WEIGHT_CHECKIN);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("Não consegui");
    }

    @Test
    @DisplayName("Deve atualizar User.pesoAtual ao registrar")
    void deveAtualizarPesoAtualNoUser() {
        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), eq(MeasurementType.PESO)))
                .thenReturn(Optional.empty());
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        handler.handle(ctx("80"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPesoAtual()).isEqualTo(80.0);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FlowContext ctx(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.REGISTRO_PESO, LocalDateTime.now());
    }

    private FlowContext ctxWithActiveFlow(String text) {
        return new FlowContext(12345L, user, state, text, IntentType.REGISTRO_PESO, LocalDateTime.now());
    }
}
