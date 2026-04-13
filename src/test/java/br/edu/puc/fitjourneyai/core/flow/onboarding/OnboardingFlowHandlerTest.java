package br.edu.puc.fitjourneyai.core.flow.onboarding;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingFlowHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OnboardingFlowHandler handler;

    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .onboardingConcluido(false)
                .build();

        state = ConversationState.builder()
                .id(1L)
                .user(user)
                .currentFlow(ConversationFlowType.ONBOARDING)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve retornar ONBOARDING como flowType")
    void deveRetornarFlowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.ONBOARDING);
    }

    @Test
    @DisplayName("Deve redirecionar para menu se onboarding já concluído")
    void deveRedirecionarSeJaConcluido() {
        user.setOnboardingConcluido(true);
        user.setNome("Igor");

        FlowResult result = handler.handle(buildContext(null, "qualquer"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("já está cadastrado");
    }

    @Test
    @DisplayName("Deve iniciar onboarding pedindo nome quando step é null")
    void deveIniciarPedindoNome() {
        state.setCurrentStep(null);

        FlowResult result = handler.handle(buildContext(null, "/start"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.ONBOARDING);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("Oi! \uD83D\uDE04 Eu sou o FitJourneyAI, seu parceiro de treinos e evolução física!");
    }

    @Test
    @DisplayName("Step 1: Deve aceitar nome e avançar para objetivo")
    void deveAceitarNome() {
        state.setCurrentStep(1);

        FlowResult result = handler.handle(buildContext(1, "Igor"));

        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(result.responseText()).contains("Prazer, Igor");
        assertThat(result.responseText()).contains("objetivo");
        assertThat(result.stateData()).containsEntry("nome", "Igor");
    }

    @Test
    @DisplayName("Step 2: Deve aceitar objetivo por número")
    void deveAceitarObjetivoPorNumero() {
        state.setCurrentStep(2);
        state.setPartialData("{\"nome\":\"Igor\"}");

        FlowResult result = handler.handle(buildContext(2, "1"));

        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.responseText()).contains("Emagrecer");
        assertThat(result.stateData()).containsEntry("objetivo", "EMAGRECER");
    }

    @Test
    @DisplayName("Step 2: Deve rejeitar objetivo inválido")
    void deveRejeitarObjetivoInvalido() {
        state.setCurrentStep(2);
        state.setPartialData("{\"nome\":\"Igor\"}");

        FlowResult result = handler.handle(buildContext(2, "voar"));

        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(result.responseText()).contains("Hmm, não consegui entender");
    }

    @Test
    @DisplayName("Step 3: Deve aceitar nível por texto")
    void deveAceitarNivelPorTexto() {
        state.setCurrentStep(3);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\"}");

        FlowResult result = handler.handle(buildContext(3, "iniciante"));

        assertThat(result.nextStep()).isEqualTo(4);
        assertThat(result.stateData()).containsEntry("nivel", "INICIANTE");
    }

    @Test
    @DisplayName("Step 4: Deve aceitar frequência válida")
    void deveAceitarFrequencia() {
        state.setCurrentStep(4);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\"}");

        FlowResult result = handler.handle(buildContext(4, "4"));

        assertThat(result.nextStep()).isEqualTo(5);
        assertThat(result.stateData()).containsEntry("frequencia", "4");
    }

    @Test
    @DisplayName("Step 4: Deve rejeitar frequência fora de range")
    void deveRejeitarFrequenciaInvalida() {
        state.setCurrentStep(4);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\"}");

        FlowResult result = handler.handle(buildContext(4, "10"));

        assertThat(result.nextStep()).isEqualTo(4);
        assertThat(result.responseText()).contains("1 e 7");
    }

    @Test
    @DisplayName("Step 5: Deve aceitar peso com vírgula")
    void deveAceitarPesoComVirgula() {
        state.setCurrentStep(5);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\"}");

        FlowResult result = handler.handle(buildContext(5, "72,5"));

        assertThat(result.nextStep()).isEqualTo(6);
        assertThat(result.stateData()).containsEntry("peso", "72.5");
    }

    @Test
    @DisplayName("Step 6: Deve aceitar 'pular' para altura opcional")
    void deveAceitarPularAltura() {
        state.setCurrentStep(6);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");

        FlowResult result = handler.handle(buildContext(6, "pular"));

        assertThat(result.nextStep()).isEqualTo(7);
        assertThat(result.responseText()).contains("Posso salvar");
        assertThat(result.stateData()).doesNotContainKey("altura");
    }

    @Test
    @DisplayName("Step 7: Confirmação 'sim' deve salvar e encerrar fluxo")
    void deveConfirmarESalvar() {
        state.setCurrentStep(7);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");

        when(userRepository.save(any(User.class))).thenReturn(user);

        FlowResult result = handler.handle(buildContext(7, "sim"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("Seu perfil tá salvo e estamos prontos pra começar!");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Step 7: 'não' deve reiniciar no step 1")
    void deveReiniciarComNao() {
        state.setCurrentStep(7);
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");

        FlowResult result = handler.handle(buildContext(7, "não"));

        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("refazer");
        assertThat(result.stateData()).isEmpty();
    }

    // ========================================================================
    // HELPER
    // ========================================================================

    private FlowContext buildContext(Integer step, String text) {
        if (step != null) {
            state.setCurrentStep(step);
        }
        return new FlowContext(
                12345L, user, state, text,
                IntentType.START, LocalDateTime.now()
        );
    }
}
