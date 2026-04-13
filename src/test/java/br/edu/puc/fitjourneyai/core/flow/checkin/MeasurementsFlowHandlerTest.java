package br.edu.puc.fitjourneyai.core.flow.checkin;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasurementsFlowHandlerTest {

    @Mock private MeasurementRepository measurementRepository;
    @Mock private UserRepository userRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MeasurementsFlowHandler handler;

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
                .currentFlow(ConversationFlowType.MEASUREMENTS_CHECKIN)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve retornar MEASUREMENTS_CHECKIN como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.MEASUREMENTS_CHECKIN);
    }

    @Test
    @DisplayName("Deve iniciar pedindo peso")
    void deveIniciarPedindoPeso() {
        state.setCurrentStep(null);
        state.setCurrentFlow(ConversationFlowType.NONE);

        FlowResult result = handler.handle(ctx(null, "/registro"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.MEASUREMENTS_CHECKIN);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("peso em kg");
    }

    @Test
    @DisplayName("Step 1: Deve aceitar peso e avançar para cintura")
    void deveAceitarPeso() {
        FlowResult result = handler.handle(ctx(1, "72.5"));

        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(result.responseText()).contains("72,5 kg");
        assertThat(result.responseText()).contains("CINTURA");
        assertThat(result.stateData()).containsEntry("peso", "72.5");
    }

    @Test
    @DisplayName("Step 1: Deve rejeitar peso inválido")
    void deveRejeitarPesoInvalido() {
        FlowResult result = handler.handle(ctx(1, "abc"));

        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("Não consegui");
    }

    @Test
    @DisplayName("Step 2: Deve aceitar cintura e avançar para quadril")
    void deveAceitarCintura() {
        state.setPartialData("{\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(2, "82"));

        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.responseText()).contains("QUADRIL");
        assertThat(result.stateData()).containsEntry("cintura", "82.0");
    }

    @Test
    @DisplayName("Step 2: Deve aceitar 'pular' e avançar para quadril")
    void devePularCintura() {
        state.setPartialData("{\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(2, "pular"));

        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.responseText()).contains("QUADRIL");
        assertThat(result.stateData()).doesNotContainKey("cintura");
    }

    @Test
    @DisplayName("Step 3: Deve aceitar quadril e avançar para peito")
    void deveAceitarQuadril() {
        state.setPartialData("{\"peso\":\"72.5\",\"cintura\":\"82.0\"}");
        FlowResult result = handler.handle(ctx(3, "98"));

        assertThat(result.nextStep()).isEqualTo(4);
        assertThat(result.responseText()).contains("PEITO");
    }

    @Test
    @DisplayName("Step 4: Deve aceitar peito e avançar para braço")
    void deveAceitarPeito() {
        state.setPartialData("{\"peso\":\"72.5\",\"cintura\":\"82.0\",\"quadril\":\"98.0\"}");
        FlowResult result = handler.handle(ctx(4, "100"));

        assertThat(result.nextStep()).isEqualTo(5);
        assertThat(result.responseText()).contains("BRAÇO");
    }

    @Test
    @DisplayName("Step 5: Deve aceitar braço e mostrar resumo para confirmação")
    void deveAceitarBracoEMostrarResumo() {
        state.setPartialData("{\"peso\":\"72.5\",\"cintura\":\"82.0\",\"quadril\":\"98.0\",\"peito\":\"100.0\"}");
        FlowResult result = handler.handle(ctx(5, "35"));

        assertThat(result.nextStep()).isEqualTo(6);
        assertThat(result.responseText()).contains("Posso salvar");
        assertThat(result.responseText()).contains("72,5 kg");
        assertThat(result.responseText()).contains("82,0 cm");
        assertThat(result.responseText()).contains("35,0 cm");
    }

    @Test
    @DisplayName("Step 6: Confirmar 'sim' deve persistir todas as medidas")
    void deveConfirmarESalvar() {
        state.setPartialData("{\"peso\":\"72.5\",\"cintura\":\"82.0\"}");

        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        FlowResult result = handler.handle(ctx(6, "sim"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("registradas com sucesso");

        // Peso + cintura = 2 saves
        verify(measurementRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Step 6: 'não' deve reiniciar o fluxo")
    void deveReiniciarComNao() {
        state.setPartialData("{\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(6, "não"));

        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("refazer");
    }

    @Test
    @DisplayName("Fluxo completo pulando tudo exceto peso — deve salvar só peso")
    void deveAceitarFluxoMinimo() {
        // Step 1: peso
        FlowResult r1 = handler.handle(ctx(1, "72.5"));
        assertThat(r1.nextStep()).isEqualTo(2);

        // Steps 2-5: pular tudo
        state.setPartialData("{\"peso\":\"72.5\"}");
        FlowResult r2 = handler.handle(ctx(2, "pular"));
        assertThat(r2.nextStep()).isEqualTo(3);

        FlowResult r3 = handler.handle(ctx(3, "pular"));
        assertThat(r3.nextStep()).isEqualTo(4);

        FlowResult r4 = handler.handle(ctx(4, "pular"));
        assertThat(r4.nextStep()).isEqualTo(5);

        FlowResult r5 = handler.handle(ctx(5, "pular"));
        assertThat(r5.nextStep()).isEqualTo(6);
        assertThat(r5.responseText()).contains("Posso salvar");

        // Step 6: confirma
        when(measurementRepository.findTopByUserAndTipoOrderByDataRegistroDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(measurementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        FlowResult r6 = handler.handle(ctx(6, "sim"));
        assertThat(r6.nextFlow()).isEqualTo(ConversationFlowType.NONE);

        // Só peso = 1 save no measurementRepository
        verify(measurementRepository, times(1)).save(any());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FlowContext ctx(Integer step, String text) {
        if (step != null) {
            state.setCurrentStep(step);
        }
        return new FlowContext(12345L, user, state, text, IntentType.REGISTRO, LocalDateTime.now());
    }
}
