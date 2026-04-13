package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Detector de intenção via IA (OpenAI).
 * <p>
 * Terceiro na cadeia (priority=20), ativado como fallback quando
 * CommandIntentDetector e KeywordIntentDetector não conseguem classificar.
 * <p>
 * Conforme Fig.13 do Pacote Consolidado: "IA classifica intenção inicial
 * e verifica aderência ao domínio".
 * <p>
 * Princípio: IA interpreta, mas NUNCA decide persistência.
 * Se a IA falhar, retorna UNKNOWN (fallback seguro).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiIntentDetector implements IntentDetector {

    private final AiService aiService;

    @Override
    public int priority() {
        return 20; // Depois de Command (0) e Keyword (10)
    }

    @Override
    public Optional<IntentType> detect(String text) {
        if (text == null || text.isBlank() || text.length() < 3) {
            return Optional.empty();
        }

        // Não classifica comandos (já tratados pelo CommandIntentDetector)
        if (text.trim().startsWith("/")) {
            return Optional.empty();
        }

        try {
            String context = "Mensagem avulsa do usuário (sem fluxo ativo)";
            IntentType intent = aiService.classifyIntent(text, context);

            if (intent == null || intent == IntentType.UNKNOWN) {
                return Optional.empty();
            }

            log.info("AiIntentDetector classificou '{}' como {}", truncate(text), intent);
            return Optional.of(intent);

        } catch (Exception e) {
            log.warn("AiIntentDetector falhou para '{}': {}", truncate(text), e.getMessage());
            return Optional.empty();
        }
    }

    private String truncate(String text) {
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
