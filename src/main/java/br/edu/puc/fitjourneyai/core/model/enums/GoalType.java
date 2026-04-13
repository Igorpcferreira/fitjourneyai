package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Objetivos principais do usuário.
 * Suporta multi-seleção no onboarding (objetivo principal + secundários).
 */
public enum GoalType {

    EMAGRECER("Emagrecer"),
    GANHAR_MUSCULO("Ganhar massa muscular"),
    MELHORAR_CONDICIONAMENTO("Melhorar condicionamento"),
    CORRER_5K_10K("Correr 5km / 10km"),
    SAUDE_BEM_ESTAR("Saúde e bem-estar"),
    GANHAR_FORCA("Ganhar força");

    private final String label;

    GoalType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Mapeia entrada do usuário para GoalType.
     * Aceita número (1-6), label parcial ou nome do enum.
     */
    public static GoalType fromUserInput(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = input.trim().toLowerCase();

        return switch (normalized) {
            case "1", "emagrecer", "perder peso", "secar" -> EMAGRECER;
            case "2", "ganhar massa", "ganhar musculo", "hipertrofia", "ganhar músculo", "massa muscular" -> GANHAR_MUSCULO;
            case "3", "melhorar condicionamento", "condicionamento", "resistência", "resistencia" -> MELHORAR_CONDICIONAMENTO;
            case "4", "correr", "corrida", "5k", "10k", "correr 5km", "correr 10km" -> CORRER_5K_10K;
            case "5", "saúde", "saude", "bem-estar", "bem estar", "qualidade de vida", "saudável", "saudavel" -> SAUDE_BEM_ESTAR;
            case "6", "força", "forca", "ganhar força", "ganhar forca", "powerlifting", "strongman" -> GANHAR_FORCA;
            default -> null;
        };
    }
}
