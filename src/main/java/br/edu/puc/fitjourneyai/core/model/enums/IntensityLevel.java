package br.edu.puc.fitjourneyai.core.model.enums;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nível de intensidade/agressividade do tom motivacional.
 * Afeta como a persona se comunica com o usuário.
 */
public enum IntensityLevel {

    LEVE(
            "Leve",
            "Motivação gentil e acolhedora",
            "Use tom GENTIL e acolhedor. Motive com encorajamento positivo. " +
            "Nunca pressione ou critique. Celebre pequenas vitórias. " +
            "Seja compreensivo com falhas e ausências."
    ),

    MODERADO(
            "Moderado",
            "Equilíbrio entre apoio e cobrança",
            "Use tom EQUILIBRADO entre apoio e cobrança. " +
            "Celebre conquistas, mas também cobre consistência. " +
            "Seja direto sem ser agressivo. Aponte áreas de melhoria com respeito."
    ),

    INTENSO(
            "Intenso",
            "Cobrança direta e sem desculpas",
            "Use tom INTENSO e cobrador. Seja direto e sem rodeios. " +
            "Não aceite desculpas facilmente. Desafie o usuário a superar limites. " +
            "Use frases de impacto. Questione quando o usuário não está dando o máximo. " +
            "Exemplos de frases no nível máximo: " +
            "'Você vai desistir de novo?', 'Seu corpo aguenta, sua mente que é fraca', " +
            "'Enquanto você descansa, alguém está treinando', 'Dor é temporária, arrependimento é eterno'. " +
            "IMPORTANTE: mesmo intenso, nunca seja desrespeitoso, ofensivo ou promova comportamento inseguro."
    );

    private final String label;
    private final String subtitle;
    private final String promptInstruction;

    IntensityLevel(String label, String subtitle, String promptInstruction) {
        this.label = label;
        this.subtitle = subtitle;
        this.promptInstruction = promptInstruction;
    }

    public String getLabel() { return label; }
    public String getSubtitle() { return subtitle; }
    public String getPromptInstruction() { return promptInstruction; }

    public static IntensityLevel fromUserInput(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = normalize(input);

        if (hasOption(normalized, "1", "um", "leve", "gentil", "suave")) return LEVE;
        if (hasOption(normalized, "2", "dois", "moderado", "medio", "equilibrado")) return MODERADO;
        if (hasOption(normalized, "3", "tres", "intenso", "maximo", "hardcore", "pesado")) return INTENSO;

        return null;
    }

    private static boolean hasOption(String input, String... options) {
        for (String option : options) {
            if (input.equals(option) || input.matches(".*\\b" + option + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
