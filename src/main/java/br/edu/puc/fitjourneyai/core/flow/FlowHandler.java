package br.edu.puc.fitjourneyai.core.flow;

import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;

/**
 * Contrato central do Strategy Pattern para fluxos conversacionais.
 * <p>
 * Cada fluxo do FitJourneyAI (onboarding, check-in, treino, etc.)
 * é implementado como um FlowHandler dedicado, registrado no
 * {@link br.edu.puc.fitjourneyai.core.orchestrator.FlowRegistry}.
 * <p>
 * O {@link br.edu.puc.fitjourneyai.core.orchestrator.ConversationOrchestrator}
 * resolve o handler adequado e delega o processamento via {@link #handle(FlowContext)}.
 */
public interface FlowHandler {

    /**
     * Retorna o tipo de fluxo que este handler processa.
     * Usado pelo FlowRegistry para resolver handlers.
     */
    ConversationFlowType getFlowType();

    /**
     * Processa uma mensagem dentro do contexto do fluxo.
     *
     * @param context dados da mensagem, usuário e estado atual
     * @return resultado com resposta, novo estado e próxima ação sugerida
     */
    FlowResult handle(FlowContext context);
}
