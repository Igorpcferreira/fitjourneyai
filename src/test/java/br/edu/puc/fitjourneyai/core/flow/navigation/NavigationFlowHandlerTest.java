package br.edu.puc.fitjourneyai.core.flow.navigation;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationFlowHandlerTest {

    private NavigationFlowHandler handler;
    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        handler = new NavigationFlowHandler();
        user = User.builder().id(1L).telegramChatId(12345L).nome("Igor")
                .onboardingConcluido(true).build();
        state = ConversationState.builder().id(1L).user(user)
                .currentFlow(ConversationFlowType.NONE).partialData("{}")
                .updatedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("Deve retornar NAVIGATION como flowType")
    void flowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.NAVIGATION);
    }

    @Test
    @DisplayName("Deve redirecionar para /start se nao concluiu onboarding")
    void deveRedirecionarSemOnboarding() {
        user.setOnboardingConcluido(false);
        FlowResult result = handler.handle(ctx("/menu", IntentType.MENU));
        assertThat(result.responseText()).contains("/start");
    }

    @Test
    @DisplayName("Deve exibir menu com emojis")
    void deveExibirMenu() {
        FlowResult result = handler.handle(ctx("/menu", IntentType.MENU));
        assertThat(result.responseText()).contains("/registro");
        assertThat(result.responseText()).contains("/treino");
        assertThat(result.responseText()).contains("/progresso");
        assertThat(result.responseText()).contains("Igor");
    }

    @Test
    @DisplayName("Deve exibir ajuda com diferenciais")
    void deveExibirAjuda() {
        FlowResult result = handler.handle(ctx("/ajuda", IntentType.AJUDA));
        assertThat(result.responseText()).contains("Eu sou o FitJourneyAI, seu parceiro de treinos e evolução física. Fui feito pra te ajudar a manter o foco e acompanhar cada conquista!");
    }

    @Test
    @DisplayName("Deve exibir config")
    void deveExibirConfig() {
        FlowResult result = handler.handle(ctx("/config", IntentType.CONFIG));
        assertThat(result.responseText()).containsIgnoringCase("config");
    }

    @Test
    @DisplayName("Deve cancelar fluxo")
    void deveCancelarFluxo() {
        FlowResult result = handler.handle(ctx("/cancelar", IntentType.CANCELAR));
        assertThat(result.responseText()).containsIgnoringCase("cancel");
    }

    @Test
    @DisplayName("Deve tratar mensagem desconhecida")
    void deveTratarDesconhecida() {
        FlowResult result = handler.handle(ctx("xyz123", IntentType.UNKNOWN));
        assertThat(result.responseText()).isNotBlank();
    }

    private FlowContext ctx(String text, IntentType intent) {
        return new FlowContext(12345L, user, state, text, intent, LocalDateTime.now());
    }
}
