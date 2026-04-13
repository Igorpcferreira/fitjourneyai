package br.edu.puc.fitjourneyai.core.flow;

import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;

import java.time.LocalDateTime;

/**
 * Contexto imutável passado a cada FlowHandler.
 * Contém todos os dados necessários para processar uma mensagem.
 *
 * @param chatId         identificador do chat no Telegram
 * @param user           usuário autenticado (carregado do banco)
 * @param state          estado conversacional atual (pode ter fluxo ativo ou NONE)
 * @param rawText        texto bruto da mensagem do usuário
 * @param detectedIntent intenção detectada pela cadeia de IntentDetectors
 * @param timestamp      momento da mensagem
 */
public record FlowContext(
        Long chatId,
        User user,
        ConversationState state,
        String rawText,
        IntentType detectedIntent,
        LocalDateTime timestamp
) {

    /**
     * Retorna o texto normalizado (trim + lowercase) para comparações.
     */
    public String normalizedText() {
        return rawText == null ? "" : rawText.trim().toLowerCase();
    }

    /**
     * Verifica se o texto bruto está vazio ou nulo.
     */
    public boolean hasText() {
        return rawText != null && !rawText.isBlank();
    }
}
