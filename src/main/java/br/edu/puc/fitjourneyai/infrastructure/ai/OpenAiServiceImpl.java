package br.edu.puc.fitjourneyai.infrastructure.ai;

import br.edu.puc.fitjourneyai.adapter.openai.OpenAiGateway;
import br.edu.puc.fitjourneyai.adapter.openai.dto.OpenAiDtos.ChatMessage;
import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.ai.PersonaPromptBuilder;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Implementação real do AiService usando OpenAI via OpenAiGateway.
 * <p>
 * Princípio invariável: IA interpreta, extrai, gera e sintetiza,
 * nunca decide persistência. Todo método tem fallback determinístico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements AiService {

    private final OpenAiGateway gateway;

    @Override
    public IntentType classifyIntent(String userMessage, String userContext) {
        String systemPrompt = """
                Você é um classificador de intenções para um chatbot de fitness chamado FitJourneyAI.
                Classifique a mensagem do usuário em UMA das seguintes categorias:
                START, MENU, AJUDA, REGISTRO, REGISTRO_PESO, REGISTRO_MEDIDAS, TREINO, TREINO_FEITO, PROGRESSO, RESUMO, CONFIG, UNKNOWN
                
                Responda APENAS com o nome da categoria, sem explicação.""";

        String userPrompt = "Contexto do usuário: " + userContext + "\nMensagem: " + userMessage;

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.0, 20
        ).map(response -> {
            try {
                return IntentType.valueOf(response.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return IntentType.UNKNOWN;
            }
        }).orElse(IntentType.UNKNOWN);
    }

    @Override
    public String generateWorkout(User user, Map<String, String> context) {
        String nome = safe(user.getNome());
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().getLabel() : "não informado";
        String nivel = user.getNivel() != null ? user.getNivel().getLabel() : "não informado";
        String freq = user.getFrequenciaTreinoEstimada() != null
                ? user.getFrequenciaTreinoEstimada() + "x por semana" : "não informado";
        String pedido = context.getOrDefault("pedido", "treino geral");
        String grupoMuscular = context.getOrDefault("grupoMuscular", "");
        String ultimosTreinos = context.getOrDefault("ultimosTreinos", "sem histórico recente");
        String duracaoSolicitada = context.getOrDefault("duracaoSolicitadaMinutos", "");
        String duracaoSolicitadaLabel = context.getOrDefault("duracaoSolicitadaLabel", "");

        String systemPrompt = PersonaPromptBuilder.buildWorkoutPrompt(user);
        String durationInstruction = duracaoSolicitada.isBlank()
                ? "Duração solicitada: não informada; estime uma duração realista para o pedido."
                : """
                Duração solicitada pelo usuário: %s.
                REGRA CRÍTICA: respeite essa duração no cabeçalho e no volume do treino. Não reduza para uma faixa genérica como 70 a 90 minutos.
                """.formatted(duracaoSolicitadaLabel);

        String userPrompt = """
                Dados do usuário:
                - Nome: %s
                - Objetivo: %s
                - Nível: %s
                - Frequência semanal: %s
                - Últimos treinos: %s
                - %s
                
                Pedido: %s
                %s
                
                Monte um treino detalhado para esse pedido, incluindo aquecimento, exercícios principais com séries/repetições, e alongamento final.
                Priorize completude sem exagerar no tamanho: não deixe nenhum exercício incompleto."""
                .formatted(
                        nome,
                        objetivo,
                        nivel,
                        freq,
                        ultimosTreinos,
                        durationInstruction,
                        pedido,
                        grupoMuscular.isBlank() ? "" : "Foco: " + grupoMuscular
                );

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.7, 4500
        ).orElseGet(() -> {
            log.warn("Fallback de treino ativado para user={}", user.getId());
            return buildFallbackWorkout(grupoMuscular, nivel, duracaoSolicitadaLabel);
        });
    }

    @Override
    public String generateMotivation(User user, Map<String, Object> indicators) {
        String nome = safe(user.getNome());
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().getLabel() : "manter forma";

        String systemPrompt = """
                Você é um coach motivacional focado em hábitos saudáveis e consistência.
                Sempre responda em português do Brasil, de forma empática, encorajadora e realista.
                Responda em no máximo 3 parágrafos curtos. Não faça promessas milagrosas.""";

        String userPrompt = "Nome: %s\nObjetivo: %s\nIndicadores: %s\n\nGere uma mensagem motivacional personalizada."
                .formatted(nome, objetivo, indicators.toString());

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.85, 400
        ).orElse("Continue firme na sua jornada! Cada treino conta e cada registro é um passo na direção certa.");
    }

    @Override
    public String generateSummary(User user, Map<String, Object> indicators) {
        String nome = safe(user.getNome());
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().getLabel() : "manter forma";

        String systemPrompt = """
                Você é o FitJourneyAI. Gere um resumo interpretativo do período de treino do usuário.
                Use tom amigável e direto. Destaque conquistas e sugira melhorias. Português do Brasil.
                Se os dados indicarem inicioJornada=true ou poucos dias acompanhados, trate como começo de acompanhamento:
                valorize o primeiro registro, fale em próximos passos e evite bronca ou diagnóstico de baixa constância.
                Máximo 4 parágrafos.""";

        String userPrompt = "Nome: %s\nObjetivo: %s\nDados do período: %s"
                .formatted(nome, objetivo, indicators.toString());

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.7, 500
        ).orElse("Resumo do período: seus dados estão sendo registrados. Continue acompanhando para ver tendências!");
    }

    @Override
    public String composeContextualResponse(String userMessage, User user, String chatHistory) {
        String nome = safe(user.getNome());
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().getLabel() : "não informado";
        String nivel = user.getNivel() != null ? user.getNivel().getLabel() : "não informado";

        String systemPrompt = PersonaPromptBuilder.buildConversationalPrompt(user);

        String userPrompt = """
                Usuario: %s | Objetivo: %s | Nivel: %s
                Historico recente:
                %s

                Mensagem: %s"""
                .formatted(
                        nome,
                        objetivo,
                        nivel,
                        chatHistory != null ? chatHistory : "sem historico",
                        userMessage
                );

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.9, 500
        ).orElse(null);
    }

    @Override
    public String composeNudgeMessage(User user, int diasInativo) {
        String nome = safe(user.getNome());
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().getLabel() : "manter forma";

        String systemPrompt = PersonaPromptBuilder.buildNudgePrompt(user);

        String userPrompt = "Nome: %s | Objetivo: %s | Dias sem interação: %d"
                .formatted(nome, objetivo, diasInativo);

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.85, 150
        ).orElse("%s, faz %d dias que você não aparece! Me manda seu peso ou pede um treino."
                .formatted(nome, diasInativo));
    }

    /**
     * Normaliza texto com possíveis typos para identificar grupo muscular.
     * Usa IA como corretor ortográfico de domínio fitness.
     */
    public String normalizeWorkoutGroup(String textoComTypo) {
        if (textoComTypo == null || textoComTypo.isBlank()) {
            return textoComTypo;
        }

        String systemPrompt = """
                Você é um corretor de texto para nomes de grupos musculares e modalidades de exercício.
                O usuário digitou o nome com possíveis erros de digitação.
                Corrija para o termo correto em português.
                Responda APENAS com o termo corrigido, sem explicação.
                Termos válidos: peito, costas, pernas, ombro, bíceps, tríceps, braços, abdômen, fullbody, cardio, corrida, caminhada, natação.
                Se não reconhecer, responda o texto original.""";

        return gateway.chatCompletion(
                        List.of(msg("system", systemPrompt), msg("user", textoComTypo)),
                        0.0, 30
                ).map(String::trim)
                .map(String::toLowerCase)
                .orElse(textoComTypo);
    }

    private String buildFallbackWorkout(String grupo, String nivel, String duracaoSolicitadaLabel) {
        String grupoLabel = grupo != null && !grupo.isBlank() ? grupo : "Treino geral";
        String duration = duracaoSolicitadaLabel != null && !duracaoSolicitadaLabel.isBlank()
                ? duracaoSolicitadaLabel
                : "45-60 min";
        return """
                Bora fazer um treino sólido e bem executado.
                
                Treino: %s
                Objetivo: treino padrão de contingência
                Nível: %s
                Intensidade: moderada
                Duração estimada: %s
                
                Aquecimento
                1) Polichinelos
                Séries: 2x30 segundos
                Descanso: 30s
                Dica: aterrisse leve e mantenha o tronco firme.
                
                2) Mobilidade articular
                Séries: 2x10 movimentos por articulação
                Descanso: 20s
                Dica: controle o movimento e aqueça sem pressa.
                
                Treino Principal
                3) Exercício composto principal
                Séries: 4x12
                Descanso: 60-90s
                Dica: mantenha técnica limpa antes de subir carga.
                
                4) Exercício complementar
                Séries: 3x15
                Descanso: 60s
                Dica: controle a fase de descida.
                
                5) Exercício isolado
                Séries: 3x12
                Descanso: 60s
                Dica: foque na contração do músculo alvo.
                
                Finalização / Alongamento
                6) Alongamento leve da região treinada
                Séries: 2x30 segundos
                Descanso: 20s
                Dica: alongue sem dor e respire fundo.
                
                Treino padrão completo. O importante é não quebrar a consistência."""
                .formatted(grupoLabel, nivel != null && !nivel.isBlank() ? nivel : "não informado", duration);
    }

    private ChatMessage msg(String role, String content) {
        return ChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "usuário";
    }
}
