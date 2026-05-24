package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Detector de intencao por palavras-chave (prioridade 10).
 * Segundo na cadeia, apos CommandIntentDetector (prioridade 0).
 *
 * REGRA CRITICA: A ordem das verificacoes importa.
 * Mais especifico primeiro, mais generico depois.
 * Palavras de REACAO (ficou, top, obrigado, valeu) NUNCA devem
 * ativar fluxos funcionais sem um pedido explicito.
 */
@Component
public class KeywordIntentDetector implements IntentDetector {

    private static final List<String> GREETING_PREFIXES = List.of(
            "oi", "ola", "eai", "fala", "salve", "bom dia", "boa tarde", "boa noite", "hey", "hello"
    );

    private static final List<String> SIMPLE_CONFIRMATIONS = List.of(
            "ok", "beleza", "blz", "certo", "entendi", "pode crer", "bora", "vamos", "sim", "nao"
    );

    @Override
    public Optional<IntentType> detect(String text) {
        if (text == null || text.isBlank()) return Optional.empty();

        String lower = normalize(text.trim().toLowerCase());

        // FILTRO GLOBAL: reacoes casuais/compromissos nunca ativam fluxos funcionais.
        // Elas vao direto para conversa contextual e nao passam pela IA classificadora,
        // que pode confundir "vou treinar" com "gere um treino".
        boolean casualOrCommitment = isCasualReaction(lower) || isTrainingCommitment(lower);
        if (casualOrCommitment && !hasExplicitFunctionalRequest(lower)) {
            return Optional.of(IntentType.CONVERSA);
        }

        // Numero isolado -> provavelmente peso
        if (isPlainNumber(lower)) {
            return Optional.of(IntentType.REGISTRO_PESO);
        }

        // ===== TREINO FEITO (mais especifico que treino) =====
        if (isTrainingDone(lower)) {
            return Optional.of(IntentType.TREINO_FEITO);
        }

        // ===== CONVERSA / GUIA (antes de treino, para "como conseguir correr 5km") =====
        if (isGuidanceQuestion(lower)) {
            return Optional.of(IntentType.CONVERSA);
        }

        // ===== TREINO (pedido de geracao) =====
        if (containsTreinoRequest(lower)) {
            return Optional.of(IntentType.TREINO);
        }

        // ===== MEDIDAS (apenas se menciona medida/parte do corpo SEM treino) =====
        if (!hasTreinoWord(lower)
                && (lower.contains("medida") || lower.contains("cintura")
                || lower.contains("quadril"))) {
            return Optional.of(IntentType.REGISTRO_MEDIDAS);
        }

        // ===== PESO =====
        if (lower.contains("peso") || lower.contains("pesagem")) {
            return Optional.of(IntentType.REGISTRO_PESO);
        }

        // ===== PROGRESSO =====
        if (lower.contains("progresso") || lower.contains("evolucao")
                || lower.contains("grafico")) {
            return Optional.of(IntentType.PROGRESSO);
        }

        // ===== RESUMO =====
        if (lower.contains("resumo") || lower.contains("relatorio")) {
            return Optional.of(IntentType.RESUMO);
        }

        // ===== AJUDA / MENU (apenas se e pedido direto, nao menção casual) =====
        if (isDirectHelpRequest(lower)) {
            return Optional.of(IntentType.AJUDA);
        }

        // ===== CANCELAR =====
        if (lower.contains("cancelar") || lower.equals("parar")
                || lower.equals("sair")) {
            return Optional.of(IntentType.CANCELAR);
        }

        // ===== CONVERSA (exercicio/tecnica/video) =====
        if (lower.contains("como fazer") || lower.contains("como executar")
                || lower.contains("como faco") || lower.contains("como faz")
                || lower.contains("como conseguir") || lower.contains("como consigo")
                || lower.contains("tecnica") || lower.contains("execucao")
                || lower.contains("video") || lower.contains("o que e")
                || lower.contains("serve pra") || lower.contains("proteina")
                || lower.contains("suplemento") || lower.contains("recuperacao")) {
            return Optional.of(IntentType.CONVERSA);
        }

        return Optional.empty();
    }

    /**
     * Detecta se a mensagem contem palavras de reacao/casual
     * que NUNCA devem ativar fluxos funcionais.
     */
    private boolean isCasualReaction(String lower) {
        // Saudacoes puras
        for (String greeting : GREETING_PREFIXES) {
            if (startsWithToken(lower, greeting)) {
                return true;
            }
        }

        String[] reactionPrefixes = {
                "beleza", "blz", "ok", "certo", "entendi", "fechou",
                "combinado", "pode deixar", "show", "perfeito", "boa",
                "valeu", "obrigado", "obrigada", "tmj"
        };
        for (String prefix : reactionPrefixes) {
            if (startsWithToken(lower, prefix)) return true;
        }

        // Agradecimentos e elogios (mesmo que contenham "treino" ou "ajuda")
        String[] reactionWords = {
                "obrigado", "obrigada", "valeu", "brigado", "vlw", "tmj",
                "ficou", "gostei", "amei", "curti", "adorei",
                "top", "show", "animal", "massa", "demais", "foda",
                "perfeito", "excelente", "muito bom", "maneiro", "legal",
                "brabo", "fera", "arrasou", "parabens", "incrivel",
                "tchau", "ate mais", "flw", "falou", "bye"
        };
        for (String word : reactionWords) {
            if (lower.contains(word)) return true;
        }

        // Confirmacoes simples
        for (String confirmation : SIMPLE_CONFIRMATIONS) {
            if (lower.equals(confirmation)) {
                return true;
            }
        }

        return false;
    }

    private boolean isTrainingCommitment(String lower) {
        if (!hasTreinoWord(lower) && !lower.contains("registro") && !lower.contains("registrar")) {
            return false;
        }

        if (containsTreinoRequest(lower) || isTrainingDone(lower)) {
            return false;
        }

        String[] commitmentPatterns = {
                "vou treinar", "eu treino", "hoje eu treino", "vou fazer o treino",
                "vou fazer esse treino", "faco o treino", "farei o treino",
                "faco o registro", "vou registrar", "farei o registro",
                "pode deixar que", "sem falta"
        };

        for (String pattern : commitmentPatterns) {
            if (lower.contains(pattern)) return true;
        }

        return false;
    }

    private boolean hasExplicitFunctionalRequest(String lower) {
        return containsTreinoRequest(lower)
                || isTrainingDone(lower)
                || isDirectHelpRequestCandidate(lower)
                || isWeightRegistrationRequest(lower)
                || isMeasureRegistrationRequest(lower)
                || isProgressOrSummaryRequest(lower)
                || lower.contains("cancelar")
                || lower.equals("parar")
                || lower.equals("sair");
    }

    private boolean isTrainingDone(String lower) {
        return lower.contains("treino feito") || lower.contains("fiz treino")
                || lower.contains("terminei o treino") || lower.contains("treinei");
    }

    private boolean isDirectHelpRequestCandidate(String lower) {
        return lower.contains("preciso de ajuda") || lower.contains("me ajuda")
                || lower.equals("ajuda") || lower.contains("como funciona");
    }

    private boolean isWeightRegistrationRequest(String lower) {
        if (!lower.contains("peso") && !lower.contains("pesagem")) {
            return false;
        }
        return hasRequestMarker(lower) || hasDigit(lower)
                || lower.contains("meu peso") || lower.contains("peso hoje");
    }

    private boolean isMeasureRegistrationRequest(String lower) {
        if (hasTreinoWord(lower)) {
            return false;
        }
        boolean hasMeasureWord = lower.contains("medida") || lower.contains("cintura")
                || lower.contains("quadril");
        return hasMeasureWord && (hasRequestMarker(lower) || hasDigit(lower)
                || lower.contains("minha cintura") || lower.contains("meu quadril"));
    }

    private boolean isProgressOrSummaryRequest(String lower) {
        boolean hasFeatureWord = lower.contains("progresso") || lower.contains("evolucao")
                || lower.contains("grafico") || lower.contains("resumo")
                || lower.contains("relatorio");
        return hasFeatureWord && (hasRequestMarker(lower)
                || lower.contains("grafico") || lower.contains("resumo")
                || lower.contains("relatorio"));
    }

    private boolean hasRequestMarker(String lower) {
        String[] markers = {
                "me manda", "manda", "mande", "quero", "queria", "preciso",
                "monta", "monte", "gera", "gere", "cria", "crie",
                "me da", "me ajuda", "pode montar", "pode gerar",
                "registrar", "registra", "anota", "anotar", "lanca",
                "lancar", "mostra", "mostrar", "ver", "consulta", "consultar"
        };
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDigit(String lower) {
        for (int i = 0; i < lower.length(); i++) {
            if (Character.isDigit(lower.charAt(i))) {
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

    private boolean isPlainNumber(String text) {
        String value = text == null ? "" : text.trim();
        if (value.endsWith("kg")) {
            value = value.substring(0, value.length() - 2).trim();
        }
        if (value.isEmpty()) {
            return false;
        }

        boolean hasDigit = false;
        boolean hasSeparator = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
                continue;
            }
            if ((c == '.' || c == ',') && !hasSeparator) {
                hasSeparator = true;
                // separador não pode ser primeiro/último
                if (i == 0 || i == value.length() - 1) {
                    return false;
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            return false;
        }

        return hasDigit;
    }

    /**
     * Remove acentos para matching robusto.
     */
    private String normalize(String text) {
        return text.replace("\u00e3", "a").replace("\u00e1", "a").replace("\u00e2", "a")
                .replace("\u00e9", "e").replace("\u00ea", "e")
                .replace("\u00ed", "i").replace("\u00f3", "o").replace("\u00f4", "o")
                .replace("\u00fa", "u").replace("\u00e7", "c")
                .replace("\u00f5", "o"); // ões -> oes
    }

    /**
     * Verifica se contem palavra de treino (incluindo variacoes sem acento).
     */
    private boolean hasTreinoWord(String lower) {
        return lower.contains("treino") || lower.contains("treinao")
                || lower.contains("exercicio") || lower.contains("workout");
    }

    /**
     * Detecta pedido real de treino com verbos em diversas conjugacoes.
     */
    private boolean containsTreinoRequest(String lower) {
        if (!hasTreinoWord(lower)) return false;

        // Verbo de PEDIDO em qualquer conjugacao
        return lower.contains("quero") || lower.contains("queria")
                || lower.contains("manda") || lower.contains("mande")
                || lower.contains("gera") || lower.contains("gere")
                || lower.contains("monta") || lower.contains("monte")
                || lower.contains("cria") || lower.contains("crie")
                || lower.contains("faz") || lower.contains("faca")
                || lower.contains("preciso") || lower.contains("me da")
                || lower.contains("pra mim") || lower.contains("para mim")
                || lower.startsWith("treino de ") || lower.startsWith("treino para ")
                || lower.startsWith("treino pra ") || lower.startsWith("um treino")
                || lower.startsWith("treinao");
    }

    private boolean isGuidanceQuestion(String lower) {
        if (!lower.startsWith("como ")) {
            return false;
        }
        return lower.contains("como fazer") || lower.contains("como executar")
                || lower.contains("como faco") || lower.contains("como faz")
                || lower.contains("como conseguir") || lower.contains("como consigo")
                || lower.contains("como melhorar") || lower.contains("como evoluir")
                || lower.contains("como chegar");
    }

    /**
     * Detecta pedido direto de ajuda (nao mencao casual como "obrigado pela ajuda").
     */
    private boolean isDirectHelpRequest(String lower) {
        if (!lower.contains("ajuda") && !lower.contains("como funciona")) {
            return false;
        }
        // "obrigado pela ajuda", "valeu pela ajuda" = casual, nao pedido de ajuda.
        // "obrigado, me ajuda..." ainda e um pedido direto.
        if (isCasualReaction(lower) && !isDirectHelpRequestCandidate(lower)) return false;

        // "preciso de ajuda", "me ajuda", "ajuda aqui" = pedido direto
        return isDirectHelpRequestCandidate(lower);
    }

    @Override
    public int priority() {
        return 10;
    }
}
