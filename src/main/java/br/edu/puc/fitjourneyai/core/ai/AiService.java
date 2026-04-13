package br.edu.puc.fitjourneyai.core.ai;

import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;

import java.util.Map;

/**
 * Porta de saída para serviços de inteligência artificial.
 * <p>
 * Define os contratos de uso da IA no FitJourneyAI.
 * Princípio invariável: a IA interpreta, extrai, gera e sintetiza —
 * NUNCA decide persistência ou cálculos.
 * <p>
 * Toda implementação deve fornecer fallback determinístico
 * para cada método em caso de falha na chamada à API.
 */
public interface AiService {

    /**
     * Classifica a intenção de uma mensagem do usuário.
     * Usado como último recurso na cadeia Command → Keyword → AI.
     *
     * @param userMessage texto da mensagem do usuário
     * @param userContext contexto resumido do usuário (nome, objetivo, etc.)
     * @return a intenção classificada, ou UNKNOWN se não conseguir
     */
    IntentType classifyIntent(String userMessage, String userContext);

    /**
     * Gera um treino personalizado baseado no perfil e histórico do usuário.
     *
     * @param user    o usuário solicitante
     * @param context contexto adicional (grupo muscular desejado, preferências, etc.)
     * @return texto do treino formatado para envio ao Telegram
     */
    String generateWorkout(User user, Map<String, String> context);

    /**
     * Gera uma mensagem motivacional personalizada baseada em indicadores.
     *
     * @param user       o usuário
     * @param indicators indicadores calculados (treinos no período, variação peso, etc.)
     * @return texto motivacional para envio
     */
    String generateMotivation(User user, Map<String, Object> indicators);

    /**
     * Gera um resumo interpretativo do período para o usuário.
     *
     * @param user       o usuário
     * @param indicators indicadores calculados do período
     * @return texto do resumo interpretativo
     */
    String generateSummary(User user, Map<String, Object> indicators);

    /**
     * Compõe uma resposta contextual curta para conversa livre
     * dentro do domínio fitness, baseada no histórico do usuário.
     *
     * @param userMessage mensagem do usuário
     * @param user        o usuário
     * @param chatHistory últimas mensagens da conversa para contexto
     * @return resposta contextual ou null se fora do domínio
     */
    String composeContextualResponse(String userMessage, User user, String chatHistory);

    /**
     * Compõe mensagem de reengajamento personalizada para nudge.
     *
     * @param user          o usuário inativo
     * @param diasInativo   quantos dias sem interação
     * @return mensagem de reengajamento com CTA
     */
    String composeNudgeMessage(User user, int diasInativo);
}
