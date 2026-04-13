package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Fonte/origem de um registro de treino.
 */
public enum WorkoutSource {

    /** Treino gerado pela IA. */
    IA,

    /** Treino registrado manualmente pelo usuário. */
    MANUAL,

    /** Treino importado do Strava. */
    STRAVA
}
