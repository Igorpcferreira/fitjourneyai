package br.edu.puc.fitjourneyai.core.flow;

import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;

import java.util.Map;

/**
 * Resultado imutável retornado por cada FlowHandler.
 * O Orchestrator usa esses dados para:
 * <ul>
 *   <li>Enviar a resposta ao Telegram (texto e/ou imagem)</li>
 *   <li>Atualizar o ConversationState (nextFlow, nextStep, stateData)</li>
 *   <li>Sugerir próxima ação ao usuário</li>
 * </ul>
 *
 * @param responseText        texto da resposta para enviar ao usuário
 * @param imageData           bytes da imagem PNG (ex: gráfico), null se não houver
 * @param imageCaption        legenda da imagem, null se não houver imagem
 * @param nextFlow            próximo fluxo (NONE se o fluxo terminou)
 * @param nextStep            próximo passo dentro do fluxo (null se terminou)
 * @param stateData           dados parciais do fluxo para persistir como JSON
 * @param suggestedNextAction sugestão de próxima ação (ex: "Use /treino para pedir um treino")
 */
public record FlowResult(
        String responseText,
        byte[] imageData,
        String imageCaption,
        ConversationFlowType nextFlow,
        Integer nextStep,
        Map<String, String> stateData,
        String suggestedNextAction
) {

    /**
     * Factory method para resposta simples de texto com transição de estado.
     */
    public static FlowResult text(String responseText,
                                  ConversationFlowType nextFlow,
                                  Integer nextStep,
                                  Map<String, String> stateData,
                                  String suggestedNextAction) {
        return new FlowResult(responseText, null, null, nextFlow, nextStep, stateData, suggestedNextAction);
    }

    /**
     * Factory method para resposta de texto que encerra o fluxo.
     */
    public static FlowResult done(String responseText, String suggestedNextAction) {
        return new FlowResult(responseText, null, null,
                ConversationFlowType.NONE, null, Map.of(), suggestedNextAction);
    }

    /**
     * Factory method para resposta com imagem que encerra o fluxo.
     */
    public static FlowResult withImage(String responseText, byte[] imageData,
                                       String imageCaption, String suggestedNextAction) {
        return new FlowResult(responseText, imageData, imageCaption,
                ConversationFlowType.NONE, null, Map.of(), suggestedNextAction);
    }

    /**
     * Verifica se há imagem para enviar.
     */
    public boolean hasImage() {
        return imageData != null && imageData.length > 0;
    }
}
