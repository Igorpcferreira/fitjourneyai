package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;

import java.util.Optional;

/**
 * Detector de intenção na cadeia de responsabilidade.
 * <p>
 * A cadeia é: CommandIntentDetector → KeywordIntentDetector → AiIntentDetector.
 * Cada detector tenta classificar; se não conseguir, retorna Optional.empty()
 * e o próximo na cadeia é consultado.
 */
public interface IntentDetector {

    /**
     * Tenta detectar a intenção a partir do texto da mensagem.
     *
     * @param text texto bruto da mensagem do usuário
     * @return a intenção detectada, ou empty se este detector não conseguiu classificar
     */
    Optional<IntentType> detect(String text);

    /**
     * Ordem de prioridade na cadeia (menor = executado primeiro).
     */
    int priority();
}
