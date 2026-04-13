package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Primeiro detector na cadeia: identifica comandos explícitos do Telegram (/start, /menu, etc.).
 * Resolução determinística e instantânea — sem chamada à IA.
 */
@Component
public class CommandIntentDetector implements IntentDetector {

    @Override
    public Optional<IntentType> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String trimmed = text.trim().toLowerCase();

        // Só processa se começa com /
        if (!trimmed.startsWith("/")) {
            return Optional.empty();
        }

        // Remove possível @botname (ex: /start@FitJourneyAIBot)
        String command = trimmed.split("@")[0];

        return switch (command) {
            case "/start" -> Optional.of(IntentType.START);
            case "/menu" -> Optional.of(IntentType.MENU);
            case "/ajuda", "/help" -> Optional.of(IntentType.AJUDA);
            case "/registro" -> Optional.of(IntentType.REGISTRO);
            case "/peso" -> Optional.of(IntentType.REGISTRO_PESO);
            case "/medidas" -> Optional.of(IntentType.REGISTRO_MEDIDAS);
            case "/treino" -> Optional.of(IntentType.TREINO);
            case "/treino_feito" -> Optional.of(IntentType.TREINO_FEITO);
            case "/progresso" -> Optional.of(IntentType.PROGRESSO);
            case "/resumo" -> Optional.of(IntentType.RESUMO);
            case "/config" -> Optional.of(IntentType.CONFIG);
            case "/cancelar" -> Optional.of(IntentType.CANCELAR);
            default -> Optional.empty();
        };
    }

    @Override
    public int priority() {
        return 0; // Primeiro na cadeia
    }
}
