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
import static org.mockito.Mockito.when;

/**
 * Testes adicionais de OnboardingFlowHandler — cobre caminhos de validação,
 * correção, múltiplos objetivos e altura com decimal.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingFlowHandler — cobertura adicional")
class OnboardingFlowHandlerExtraTest {

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
                .id(1L).user(user)
                .currentFlow(ConversationFlowType.ONBOARDING)
                .partialData("{}").updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── Step 1 — nome vazio ───────────────────────────────────────────────

    @Test
    @DisplayName("Step 1: nome em branco deve pedir novamente")
    void step1_nomeEmBranco() {
        FlowResult result = handler.handle(ctx(1, ""));
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("nome");
    }

    @Test
    @DisplayName("Step 1: frase natural de áudio deve extrair apenas o nome")
    void step1_fraseNaturalExtraiNome() {
        FlowResult result = handler.handle(ctx(1, "Pode me chamar de IGor."));
        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(result.stateData()).containsEntry("nome", "Igor");
        assertThat(result.responseText()).contains("Prazer, Igor");
        assertThat(result.responseText()).doesNotContain("Pode me chamar");
    }

    // ─── Step 2 — múltiplos objetivos ─────────────────────────────────────

    @Test
    @DisplayName("Step 2: múltiplos objetivos separados por vírgula")
    void step2_multiplosObjetivosPorVirgula() {
        state.setPartialData("{\"nome\":\"Igor\"}");
        FlowResult result = handler.handle(ctx(2, "1,3"));
        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.stateData()).containsEntry("objetivo", "EMAGRECER");
        assertThat(result.stateData()).containsKey("objetivosSecundarios");
    }

    @Test
    @DisplayName("Step 2: múltiplos objetivos separados por 'e'")
    void step2_multiplosObjetivosPorE() {
        state.setPartialData("{\"nome\":\"Igor\"}");
        FlowResult result = handler.handle(ctx(2, "2 e 6"));
        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.stateData()).containsEntry("objetivo", "GANHAR_MUSCULO");
    }

    @Test
    @DisplayName("Step 2: objetivos falados por extenso devem funcionar")
    void step2_multiplosObjetivosPorAudioExtenso() {
        state.setPartialData("{\"nome\":\"Igor\"}");
        FlowResult result = handler.handle(ctx(2, "dois e seis"));
        assertThat(result.nextStep()).isEqualTo(3);
        assertThat(result.stateData()).containsEntry("objetivo", "GANHAR_MUSCULO");
        assertThat(result.stateData().get("objetivosSecundarios")).contains("for");
    }

    // ─── Step 3 — nível inválido ───────────────────────────────────────────

    @Test
    @DisplayName("Step 3: nível inválido deve pedir novamente")
    void step3_nivelInvalido() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\"}");
        FlowResult result = handler.handle(ctx(3, "super pro"));
        assertThat(result.nextStep()).isEqualTo(3);
    }

    @Test
    @DisplayName("Step 3: nível avançado por número")
    void step3_nivelAvancadoPorNumero() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\"}");
        FlowResult result = handler.handle(ctx(3, "3"));
        assertThat(result.nextStep()).isEqualTo(4);
        assertThat(result.stateData()).containsEntry("nivel", "AVANCADO");
    }

    // ─── Step 4 — frequência ──────────────────────────────────────────────

    @Test
    @DisplayName("Step 4: frequência com letra deve falhar")
    void step4_frequenciaComLetra() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\"}");
        FlowResult result = handler.handle(ctx(4, "muitas"));
        assertThat(result.nextStep()).isEqualTo(4);
    }

    // ─── Step 5 — peso ────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 5: peso inválido retorna ao mesmo step")
    void step5_pesoInvalido() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\"}");
        FlowResult result = handler.handle(ctx(5, "abc"));
        assertThat(result.nextStep()).isEqualTo(5);
        assertThat(result.responseText()).contains("peso");
    }

    @Test
    @DisplayName("Step 5: peso abaixo do mínimo (<20) é inválido")
    void step5_pesoAbaixoDoMinimo() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\"}");
        FlowResult result = handler.handle(ctx(5, "10"));
        assertThat(result.nextStep()).isEqualTo(5);
    }

    // ─── Step 6 — altura ──────────────────────────────────────────────────

    @Test
    @DisplayName("Step 6: altura com decimal aceita")
    void step6_alturaDecimal() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(6, "175,5"));
        assertThat(result.nextStep()).isEqualTo(7);
        assertThat(result.stateData()).containsEntry("altura", "176");
    }

    @Test
    @DisplayName("Step 6: altura inválida deve pedir novamente")
    void step6_alturaInvalida() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(6, "trinta"));
        assertThat(result.nextStep()).isEqualTo(6);
    }

    @Test
    @DisplayName("Step 6: altura fora do range (>250) é inválida")
    void step6_alturaForaDoRange() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(6, "300"));
        assertThat(result.nextStep()).isEqualTo(6);
    }

    // ─── Step 7 — confirmação ─────────────────────────────────────────────

    @Test
    @DisplayName("Step 7: resposta ambígua pede sim/não novamente")
    void step7_respostaAmbigua() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(7, "talvez"));
        assertThat(result.nextStep()).isEqualTo(7);
        assertThat(result.responseText()).contains("sim");
    }

    @Test
    @DisplayName("Step 7: confirmação com 'beleza' deve salvar")
    void step7_confirmacaoComBeleza() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\",\"altura\":\"175\"}");
        when(userRepository.save(any(User.class))).thenReturn(user);
        FlowResult result = handler.handle(ctx(7, "beleza"));
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("Perfeito");
    }

    // ─── Correção / voltar ────────────────────────────────────────────────

    @Test
    @DisplayName("'voltar nome' no step 3 deve retornar ao step 1")
    void voltarNomeDoStep3() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\"}");
        FlowResult result = handler.handle(ctx(3, "quero corrigir nome"));
        assertThat(result.nextStep()).isEqualTo(1);
    }

    @Test
    @DisplayName("'corrigir peso' no step 6 deve retornar ao step 5")
    void corrigirPesoDoStep6() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\",\"frequencia\":\"4\",\"peso\":\"72.5\"}");
        FlowResult result = handler.handle(ctx(6, "corrigir peso"));
        assertThat(result.nextStep()).isEqualTo(5);
    }

    @Test
    @DisplayName("'voltar' genérico no step 4 deve recuar um step")
    void voltarGenericoDoStep4() {
        state.setPartialData("{\"nome\":\"Igor\",\"objetivo\":\"EMAGRECER\",\"nivel\":\"INICIANTE\"}");
        FlowResult result = handler.handle(ctx(4, "voltar"));
        assertThat(result.nextStep()).isEqualTo(3);
    }

    // ─── step inválido fora do range ──────────────────────────────────────

    @Test
    @DisplayName("Step inválido (ex: 99) deve reiniciar onboarding")
    void stepInvalidoDeveReiniciar() {
        state.setCurrentStep(99);
        FlowResult result = handler.handle(ctx(99, "qualquer"));
        assertThat(result.nextStep()).isEqualTo(1);
    }

    // ─── HELPER ───────────────────────────────────────────────────────────

    private FlowContext ctx(Integer step, String text) {
        state.setCurrentStep(step);
        return new FlowContext(12345L, user, state, text, IntentType.START, LocalDateTime.now());
    }
}

