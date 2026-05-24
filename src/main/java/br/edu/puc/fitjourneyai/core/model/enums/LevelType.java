package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Nível de experiência do usuário com exercícios físicos.
 */
public enum LevelType {

    INICIANTE("Iniciante"),
    INTERMEDIARIO("Intermediário"),
    AVANCADO("Avançado");

    private final String label;

    LevelType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Tenta mapear uma entrada do usuário para um LevelType.
     */
    public static LevelType fromUserInput(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = input.trim().toLowerCase();

        return switch (normalized) {
            case "1", "um", "uma", "iniciante", "beginner", "começo" -> INICIANTE;
            case "2", "dois", "duas", "intermediario", "intermediário", "médio", "medio", "intermediate" -> INTERMEDIARIO;
            case "3", "tres", "três", "avancado", "avançado", "advanced", "experiente" -> AVANCADO;
            default -> null;
        };
    }
}
