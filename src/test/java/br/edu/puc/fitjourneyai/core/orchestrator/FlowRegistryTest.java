package br.edu.puc.fitjourneyai.core.orchestrator;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRegistryTest {

    @Test
    @DisplayName("Deve registrar e resolver handler")
    void deveRegistrarEResolver() {
        FlowHandler handler = dummyHandler(ConversationFlowType.ONBOARDING);
        FlowRegistry registry = new FlowRegistry(List.of(handler));

        Optional<FlowHandler> resolved = registry.resolve(ConversationFlowType.ONBOARDING);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getFlowType()).isEqualTo(ConversationFlowType.ONBOARDING);
    }

    @Test
    @DisplayName("Deve retornar vazio para tipo nao registrado")
    void deveRetornarVazioParaNaoRegistrado() {
        FlowRegistry registry = new FlowRegistry(List.of());
        assertThat(registry.resolve(ConversationFlowType.ONBOARDING)).isEmpty();
    }

    @Test
    @DisplayName("Deve verificar existencia de handler")
    void deveVerificarExistencia() {
        FlowHandler handler = dummyHandler(ConversationFlowType.WEIGHT_CHECKIN);
        FlowRegistry registry = new FlowRegistry(List.of(handler));

        assertThat(registry.hasHandler(ConversationFlowType.WEIGHT_CHECKIN)).isTrue();
        assertThat(registry.hasHandler(ConversationFlowType.PROGRESS)).isFalse();
    }

    @Test
    @DisplayName("Deve registrar multiplos handlers")
    void deveRegistrarMultiplos() {
        FlowRegistry registry = new FlowRegistry(List.of(
                dummyHandler(ConversationFlowType.ONBOARDING),
                dummyHandler(ConversationFlowType.WEIGHT_CHECKIN),
                dummyHandler(ConversationFlowType.NAVIGATION)
        ));

        assertThat(registry.hasHandler(ConversationFlowType.ONBOARDING)).isTrue();
        assertThat(registry.hasHandler(ConversationFlowType.WEIGHT_CHECKIN)).isTrue();
        assertThat(registry.hasHandler(ConversationFlowType.NAVIGATION)).isTrue();
    }

    private FlowHandler dummyHandler(ConversationFlowType type) {
        return new FlowHandler() {
            @Override public ConversationFlowType getFlowType() { return type; }
            @Override public FlowResult handle(FlowContext context) { return FlowResult.done("test", null); }
        };
    }
}
