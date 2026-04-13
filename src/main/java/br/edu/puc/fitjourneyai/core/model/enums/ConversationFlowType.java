package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Tipos de fluxo conversacional do FitJourneyAI.
 * Cada valor corresponde a um FlowHandler dedicado.
 */
public enum ConversationFlowType {

    /** Nenhum fluxo ativo — estado ocioso. */
    NONE,

    /** Fluxo 1 — Onboarding e contextualização inicial. */
    ONBOARDING,

    /** Fluxo 3a — Check-in corporal: registro de peso. */
    WEIGHT_CHECKIN,

    /** Fluxo 3b — Check-in corporal: registro de medidas completas. */
    MEASUREMENTS_CHECKIN,

    /** Fluxo 4 — Registro manual de atividade multimodal. */
    ACTIVITY_REGISTRATION,

    /** Fluxo 5 — Geração de treino personalizado com IA. */
    WORKOUT_GENERATION,

    /** Fluxo 6 — Evolução e leitura de progresso. */
    PROGRESS,

    /** Fluxo 7 — Resumos inteligentes. */
    SUMMARY,

    /** Fluxo 8 — Interação conversacional contextual. */
    CONTEXTUAL_CONVERSATION,

    /** Fluxo de configuracao de persona e intensidade. */
    CONFIG,

    /** Fluxo 2 — Navegação e recuperação (menu, ajuda, fallback). */
    NAVIGATION
}
