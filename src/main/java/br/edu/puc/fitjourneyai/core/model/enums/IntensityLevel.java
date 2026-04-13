package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Nivel de intensidade/agressividade do tom motivacional.
 * Afeta como a persona se comunica com o usuario.
 */
public enum IntensityLevel {

    LEVE(
            "Leve",
            "Motivacao gentil e acolhedora",
            "Use tom GENTIL e acolhedor. Motive com encorajamento positivo. " +
            "Nunca pressione ou critique. Celebre pequenas vitorias. " +
            "Seja compreensivo com falhas e ausencias."
    ),

    MODERADO(
            "Moderado",
            "Equilibrio entre apoio e cobranca",
            "Use tom EQUILIBRADO entre apoio e cobranca. " +
            "Celebre conquistas mas tambem cobre consistencia. " +
            "Seja direto sem ser agressivo. Aponte areas de melhoria com respeito."
    ),

    INTENSO(
            "Intenso",
            "Cobranca direta e sem desculpas",
            "Use tom INTENSO e cobrador. Seja direto e sem rodeios. " +
            "Nao aceite desculpas facilmente. Desafie o usuario a superar limites. " +
            "Use frases de impacto. Questione quando o usuario nao esta dando o maximo. " +
            "Exemplos de frases no nivel maximo: " +
            "'Voce vai desistir de novo?', 'Seu corpo aguenta, sua mente que e fraca', " +
            "'Enquanto voce descansa, alguem esta treinando', 'Dor e temporaria, arrependimento e eterno'. " +
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
        String normalized = input.trim().toLowerCase();

        return switch (normalized) {
            case "1", "leve", "gentil", "suave" -> LEVE;
            case "2", "moderado", "medio", "equilibrado" -> MODERADO;
            case "3", "intenso", "maximo", "hardcore", "pesado" -> INTENSO;
            default -> null;
        };
    }
}
