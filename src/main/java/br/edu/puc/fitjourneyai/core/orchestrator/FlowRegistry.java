package br.edu.puc.fitjourneyai.core.orchestrator;

import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro central de FlowHandlers.
 * <p>
 * Recebe todos os handlers via injeção de dependência do Spring
 * e os indexa por {@link ConversationFlowType} para resolução O(1).
 */
@Slf4j
@Component
public class FlowRegistry {

    private final Map<ConversationFlowType, FlowHandler> handlers;

    /**
     * O Spring injeta automaticamente todas as implementações de FlowHandler.
     */
    public FlowRegistry(List<FlowHandler> flowHandlers) {
        this.handlers = new EnumMap<>(ConversationFlowType.class);

        for (FlowHandler handler : flowHandlers) {
            ConversationFlowType type = handler.getFlowType();
            if (handlers.containsKey(type)) {
                log.warn("FlowHandler duplicado para tipo {}: {} será sobrescrito por {}",
                        type, handlers.get(type).getClass().getSimpleName(),
                        handler.getClass().getSimpleName());
            }
            handlers.put(type, handler);
            log.info("FlowHandler registrado: {} → {}", type, handler.getClass().getSimpleName());
        }

        log.info("FlowRegistry inicializado com {} handlers", handlers.size());
    }

    /**
     * Resolve o handler para o tipo de fluxo informado.
     *
     * @param flowType tipo do fluxo
     * @return Optional com o handler, ou vazio se não registrado
     */
    public Optional<FlowHandler> resolve(ConversationFlowType flowType) {
        return Optional.ofNullable(handlers.get(flowType));
    }

    /**
     * Verifica se existe um handler registrado para o tipo.
     */
    public boolean hasHandler(ConversationFlowType flowType) {
        return handlers.containsKey(flowType);
    }
}
