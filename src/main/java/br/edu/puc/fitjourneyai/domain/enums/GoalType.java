package br.edu.puc.fitjourneyai.domain.enums;

public enum GoalType {
    EMAGRECER("Emagrecer"),
    GANHAR_MUSCULO("Ganhar massa muscular"),
    MELHORAR_CONDICIONAMENTO("Melhorar condicionamento"),
    CORRER_5K_10K("Correr 5km / 10km");

    private final String label;

    GoalType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // já pode manter esse helper aqui mesmo
    public static GoalType fromUserInput(String input) {
        if (input == null) return null;

        String norm = input.trim().toLowerCase();

        if (norm.contains("emag")) return EMAGRECER;
        if (norm.contains("massa") || norm.contains("musculo") || norm.contains("músculo")) return GANHAR_MUSCULO;
        if (norm.contains("cond") || norm.contains("cardio")) return MELHORAR_CONDICIONAMENTO;
        if (norm.contains("5") || norm.contains("10") || norm.contains("correr") || norm.contains("corrida"))
            return CORRER_5K_10K;

        return null;
    }
}
