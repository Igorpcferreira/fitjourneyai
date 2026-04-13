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

        String systemPrompt = PersonaPromptBuilder.buildWorkoutPrompt(user);

        String userPrompt = """
                Dados do usuário:
                - Nome: %s
                - Objetivo: %s
                - Nível: %s
                - Frequência semanal: %s
                - Últimos treinos: %s
                
                Pedido: %s
                %s
                
                Monte um treino detalhado para esse pedido, incluindo aquecimento, exercícios principais com séries/repetições, e alongamento final."""
                .formatted(
                        nome,
                        objetivo,
                        nivel,
                        freq,
                        ultimosTreinos,
                        pedido,
                        grupoMuscular.isBlank() ? "" : "Foco: " + grupoMuscular
                );

        return gateway.chatCompletion(
                List.of(msg("system", systemPrompt), msg("user", userPrompt)),
                0.7, 1000
        ).orElseGet(() -> {
            log.warn("Fallback de treino ativado para user={}", user.getId());
            return buildFallbackWorkout(grupoMuscular, nivel);
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

    private String buildFallbackWorkout(String grupo, String nivel) {
        String grupoLabel = grupo != null && !grupo.isBlank() ? grupo : "Treino geral";
        return """
                %s (treino padrão)
                
                Aquecimento (5 min):
                - Polichinelos: 2x30
                - Mobilidade articular
                
                Exercícios principais:
                1. Exercício composto principal - 4x12
                2. Exercício complementar - 3x15
                3. Exercício isolado - 3x12
                4. Exercício de finalização - 3x15
                
                Descanso: 60-90s entre séries
                
                Alongamento (5 min)
                
                (Este é um treino padrão. Tente novamente com /treino para um treino personalizado com IA.)"""
                .formatted(grupoLabel);
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
