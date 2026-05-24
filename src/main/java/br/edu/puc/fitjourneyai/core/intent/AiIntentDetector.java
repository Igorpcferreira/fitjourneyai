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

        if (isCasualContinuation(text)) {
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

    private boolean isCasualContinuation(String text) {
        String normalized = normalize(text);
        if (hasExplicitRequest(normalized)) {
            return false;
        }

        String[] casualPrefixes = {
                "beleza", "blz", "ok", "certo", "entendi", "fechou",
                "combinado", "pode deixar", "show", "perfeito", "boa",
                "valeu", "obrigado", "obrigada", "tmj"
        };
        for (String prefix : casualPrefixes) {
            if (startsWithToken(normalized, prefix)) {
                return true;
            }
        }

        String[] commitmentPatterns = {
                "vou treinar", "eu treino", "hoje eu treino", "vou fazer o treino",
                "vou fazer esse treino", "faco o treino", "farei o treino",
                "faco o registro", "vou registrar", "farei o registro",
                "pode deixar que", "sem falta"
        };
        for (String pattern : commitmentPatterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasExplicitRequest(String normalized) {
        String[] requestMarkers = {
                "me manda", "manda", "mande", "quero", "queria", "preciso",
                "monta", "monte", "gera", "gere", "cria", "crie",
                "me da", "me ajuda", "pode montar", "pode gerar"
        };
        for (String marker : requestMarkers) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean startsWithToken(String text, String token) {
        if (!text.startsWith(token)) {
            return false;
        }
        if (text.length() == token.length()) {
            return true;
        }
        char next = text.charAt(token.length());
        return Character.isWhitespace(next) || !Character.isLetterOrDigit(next);
    }

    private String normalize(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
