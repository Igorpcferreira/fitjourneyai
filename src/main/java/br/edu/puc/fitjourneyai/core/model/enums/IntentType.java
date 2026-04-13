package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Intenções reconhecidas pela camada de detecção de intenção.
 * A cadeia de detecção é: CommandIntentDetector → KeywordIntentDetector → AiIntentDetector.
 */
public enum IntentType {

    /** /start — inicia onboarding ou mostra menu se já cadastrado. */
    START,

    /** /menu — exibe opções disponíveis. */
    MENU,

    /** /ajuda — mostra informações de ajuda. */
    AJUDA,

    /** /registro — inicia registro guiado de medidas completas. */
    REGISTRO,

    /** /peso ou número isolado — registro rápido de peso. */
    REGISTRO_PESO,

    /** /medidas — registro guiado de medidas corporais. */
    REGISTRO_MEDIDAS,

    /** /treino — solicita geração de treino personalizado. */
    TREINO,

    /** /treino_feito — registra atividade/treino realizado. */
    TREINO_FEITO,

    /** /progresso — consulta evolução e gráficos. */
    PROGRESSO,

    /** /resumo — solicita resumo do período. */
    RESUMO,

    /** /config — ajustar preferências. */
    CONFIG,

    /** /cancelar — cancela fluxo em andamento. */
    CANCELAR,

    /** Conversa contextual — perguntas livres sobre fitness, exercícios, etc. */
    CONVERSA,

    /** Intenção não identificada — fallback. */
    UNKNOWN
}
