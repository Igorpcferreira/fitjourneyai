package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Detector de intencao por palavras-chave (prioridade 10).
 * Segundo na cadeia, apos CommandIntentDetector (prioridade 0).
 *
 * REGRA CRITICA: A ordem das verificacoes importa.
 * Mais especifico primeiro, mais generico depois.
 * Palavras de REACAO (ficou, top, obrigado, valeu) NUNCA devem
 * ativar fluxos funcionais - devem cair na IA.
 */
@Component
public class KeywordIntentDetector implements IntentDetector {

    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\d+([.,]\\d+)?\\s*(kg)?\\s*$");

    @Override
    public Optional<IntentType> detect(String text) {
        if (text == null || text.isBlank()) return Optional.empty();

        String lower = normalize(text.trim().toLowerCase());

        // FILTRO GLOBAL: reacoes casuais nunca ativam fluxos
        if (isCasualReaction(lower)) {
            return Optional.empty(); // Cai na IA como conversa contextual
        }

        // Numero isolado -> provavelmente peso
        if (PLAIN_NUMBER.matcher(lower).matches()) {
            return Optional.of(IntentType.REGISTRO_PESO);
        }

        // ===== TREINO FEITO (mais especifico que treino) =====
        if (lower.contains("treino feito") || lower.contains("fiz treino")
                || lower.contains("terminei o treino") || lower.contains("treinei")) {
            return Optional.of(IntentType.TREINO_FEITO);
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
        if (lower.matches("^(oi|ola|eai|fala|salve|bom dia|boa tarde|boa noite|hey|hello)\\b.*")) {
            return true;
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
        if (lower.matches("^(ok|beleza|blz|certo|entendi|pode crer|bora|vamos|sim|nao)$")) {
            return true;
        }

        return false;
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

    /**
     * Detecta pedido direto de ajuda (nao mencao casual como "obrigado pela ajuda").
     */
    private boolean isDirectHelpRequest(String lower) {
        if (!lower.contains("ajuda") && !lower.contains("como funciona")) {
            return false;
        }
        // "obrigado pela ajuda", "valeu pela ajuda" = casual, nao pedido de ajuda
        if (isCasualReaction(lower)) return false;

        // "preciso de ajuda", "me ajuda", "ajuda aqui" = pedido direto
        return lower.contains("preciso") || lower.contains("me ajuda")
                || lower.equals("ajuda") || lower.contains("como funciona");
    }

    @Override
    public int priority() {
        return 10;
    }
}
