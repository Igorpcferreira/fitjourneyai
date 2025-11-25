package br.edu.puc.fitjourneyai.domain.enums;

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
}
