package br.edu.puc.fitjourneyai.core.flow.conversation;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.Message;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.port.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fluxo 8 — Interação Conversacional Contextual.
 * <p>
 * Arquitetura:
 * <ol>
 *   <li>Detecta exercício/técnica/vídeo → resposta com YouTube (dinâmico)</li>
 *   <li>Tudo mais → IA com persona de coach fitness motivacional</li>
 * </ol>
 * <p>
 * Não usa mais respostas hardcoded para conversa casual.
 * TODA conversa vai para a IA, que responde como um coach real,
 * mantendo contexto e direcionando para funcionalidades do bot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextualConversationFlowHandler implements FlowHandler {

    private final AiService aiService;
    private final MessageRepository messageRepository;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.CONTEXTUAL_CONVERSATION;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Ei, que bom te ver por aqui! 😄\n\nAntes de a gente começar, preciso te conhecer melhor pra personalizar tudo pra você.\n\nManda um /start e em menos de 1 minuto a gente resolve isso! 🚀",
                    "Use /start para iniciar o cadastro."
            );
        }

        String userMessage = context.rawText();
        if (userMessage == null || userMessage.isBlank()) {
            return FlowResult.done(
                    "Me conta, o que você quer saber? Posso te ajudar com treinos, exercícios, nutrição fitness e evolução! \uD83D\uDCAA",
                    null
            );
        }

        // 1. Exercício/técnica/vídeo (extração dinâmica — ANTES da IA)
        String exerciseResponse = handleExerciseQuery(userMessage);
        if (exerciseResponse != null) {
            return FlowResult.done(exerciseResponse,
                    "Quer saber sobre outro exercício? Ou manda /treino pra eu montar um treino completo!");
        }

        // 2. TUDO mais → IA com persona de coach motivacional
        return handleAiConversation(userMessage, user);
    }

    // ========================================================================
    // 1. EXERCÍCIO / TÉCNICA / VÍDEO — EXTRAÇÃO DINÂMICA
    // ========================================================================

    private String handleExerciseQuery(String message) {
        String lower = message.toLowerCase().trim();

        if (!isExerciseIntent(lower)) return null;

        String exerciseTerm = extractExerciseTerm(lower);
        if (exerciseTerm == null || exerciseTerm.length() < 2) return null;

        log.info("Exercício detectado: '{}'", exerciseTerm);

        CuratedExercise curated = findCurated(exerciseTerm);
        String youtubeUrl = buildYoutubeSearchUrl(exerciseTerm + " execução correta");

        if (curated != null) {
            return buildEnrichedResponse(curated, youtubeUrl);
        } else {
            return buildDynamicResponse(exerciseTerm, youtubeUrl);
        }
    }

    private boolean isExerciseIntent(String lower) {
        return EXERCISE_INTENT_PATTERNS.stream().anyMatch(p -> p.matcher(lower).find());
    }

    /** Padrões ampliados para detectar intenção de exercício/técnica. */
    private static final List<Pattern> EXERCISE_INTENT_PATTERNS = List.of(
            Pattern.compile("como (fazer|executar|faz|realizar|melhorar)"),
            Pattern.compile("(me )?(mostra|explica|ensina)"),
            Pattern.compile("(técnica|tecnica|execução|execucao) (de|do|da|no|na)"),
            Pattern.compile("(vídeo|video) (de|do|da|sobre)"),
            Pattern.compile("(o que é|o que e|oque é|oque e)"),
            Pattern.compile("quero (aprender|ver|saber)"),
            Pattern.compile("como (funciona|é|e) (um |uma |o |a )"),
            // Novos padrões que faltavam:
            Pattern.compile("(me )?manda (um )?(vídeo|video)"),
            Pattern.compile("como (se )?(faz|executa|realiza)"),
            Pattern.compile("(qual|como).*técnica")
    );

    private String extractExerciseTerm(String lower) {
        String[] prefixes = {
                "me manda (um )?(vídeo|video) (de )?(como )?(fazer|executar )?(um |uma |o |a )?",
                "como (se )?(fazer|executar|faz|realizar) (um |uma |o |a )?",
                "como melhorar (minha |meu )?(técnica |tecnica )?(de |do |da |no |na |em )?",
                "me (mostra|explica|ensina) (um |uma )?(vídeo |video )?(de |do |da |sobre )?(como )?(fazer |executar )?",
                "quero (aprender|ver|saber) (sobre |como )?(fazer |executar )?(um |uma |o |a )?",
                "(técnica|tecnica|execução|execucao) (de |do |da |no |na )",
                "(vídeo|video) (de |do |da |sobre )",
                "(o que |oque )(é|e) (um |uma |o |a )?",
                "como (funciona|é|e) (um |uma |o |a )?",
        };

        String result = lower;
        for (String prefix : prefixes) {
            result = result.replaceFirst("^" + prefix, "").trim();
        }

        result = result.replaceAll("\\?+$", "")
                .replaceAll("(corretamente|certinho|direito|certo|correto)$", "")
                .replaceAll("(por favor|pfv|pf)$", "")
                .trim();

        if (result.equals(lower) || result.length() > 60) return null;
        return result.isBlank() ? null : result;
    }

    private String buildYoutubeSearchUrl(String query) {
        return "https://www.youtube.com/results?search_query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private String buildEnrichedResponse(CuratedExercise ex, String youtubeUrl) {
        return String.format("""
                \uD83C\uDFCB\uFE0F %s
                
                \uD83D\uDCDD %s
                
                \uD83C\uDFAF Músculos: %s
                
                \uD83C\uDFA5 Vídeo de referência:
                %s
                
                \u26A0\uFE0F Dica: comece com carga leve pra dominar a técnica!""",
                ex.name, ex.description, ex.muscles, youtubeUrl);
    }

    private String buildDynamicResponse(String exerciseTerm, String youtubeUrl) {
        String termCap = exerciseTerm.substring(0, 1).toUpperCase() + exerciseTerm.substring(1);
        return String.format("""
                \uD83C\uDFCB\uFE0F %s
                
                Encontrei referências de como executar esse exercício!
                
                \uD83C\uDFA5 Vídeo de referência:
                %s
                
                \uD83D\uDCA1 Dica: assista o vídeo com atenção e comece sempre com carga leve.
                
                Quer que eu monte um treino incluindo esse exercício? Manda /treino!""",
                termCap, youtubeUrl);
    }

    // ========================================================================
    // 2. CONVERSA VIA IA (casual + contextual + motivacional)
    // ========================================================================

    /**
     * TODA mensagem que não é exercício vai para a IA.
     * A IA responde como coach fitness com persona motivacional,
     * mantendo contexto do histórico e direcionando para funcionalidades.
     */
    private FlowResult handleAiConversation(String userMessage, User user) {
        String chatHistory = buildChatHistory(user);

        try {
            String aiResponse = aiService.composeContextualResponse(userMessage, user, chatHistory);

            if (aiResponse != null && !aiResponse.isBlank()) {
                log.info("Conversa IA: user={}", user.getId());
                return FlowResult.done(aiResponse, null);
            }
        } catch (Exception e) {
            log.warn("Erro na conversa IA: {}", e.getMessage());
        }

        // Fallback quando IA falha completamente
        String nome = user.getNome() != null ? user.getNome() : "amigo";
        return FlowResult.done(
                nome + ", não consegui processar agora \uD83D\uDE05 Tenta de novo ou usa /menu pra ver o que posso fazer!",
                null
        );
    }

    private String buildChatHistory(User user) {
        try {
            List<Message> recent = messageRepository.findTop10ByUserOrderByDataHoraDesc(user);
            if (recent.isEmpty()) return "sem histórico";
            return recent.stream()
                    .map(m -> m.getTipo().name() + ": " + truncate(m.getConteudo(), 80))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "sem histórico";
        }
    }

    // ========================================================================
    // CATÁLOGO CURADO (enriquecimento opcional)
    // ========================================================================

    private record CuratedExercise(String name, String description, String muscles) {}

    private CuratedExercise findCurated(String term) {
        String normalized = term.toLowerCase()
                .replaceAll("[áàâã]", "a").replaceAll("[éèê]", "e")
                .replaceAll("[íìî]", "i").replaceAll("[óòôõ]", "o")
                .replaceAll("[úùû]", "u").replaceAll("ç", "c");

        for (Map.Entry<String, CuratedExercise> entry : CURATED_CATALOG.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static final Map<String, CuratedExercise> CURATED_CATALOG = Map.ofEntries(
            e("supino reto", "Supino Reto", "Deite no banco, pés no chão. Barra na largura dos ombros, desça até o peito, empurre explosivo.", "Peitoral, tríceps, deltóide anterior"),
            e("supino inclinado", "Supino Inclinado", "Banco a 30-45°, halteres na altura do peito, empurre convergindo no topo.", "Peitoral superior, tríceps"),
            e("crucifixo", "Crucifixo", "Banco plano, braços abertos com leve flexão. Abra até alongar o peito.", "Peitoral (abertura)"),
            e("crossover", "Cross-over", "Polias altas, tronco inclinado. Puxe em arco até as mãos se encontrarem.", "Peitoral"),
            e("flexao", "Flexão de Braços", "Mãos na largura dos ombros, corpo reto. Desça até quase tocar o chão.", "Peitoral, tríceps, core"),
            e("puxada", "Puxada Frontal", "Pegada pronada, largura maior que ombros. Puxe até o queixo.", "Dorsal, bíceps, rombóides"),
            e("remada curvada", "Remada Curvada", "Tronco a 45°, puxe a barra até o abdômen.", "Dorsal, trapézio, bíceps"),
            e("barra fixa", "Barra Fixa", "Pegada pronada, puxe o corpo até o queixo ultrapassar a barra.", "Dorsal, bíceps, core"),
            e("agachamento", "Agachamento Livre", "Barra no trapézio, pés na largura dos ombros. Desça até 90°.", "Quadríceps, glúteos, core"),
            e("leg press", "Leg Press 45°", "Pés na plataforma, desça controlado até 90° nos joelhos.", "Quadríceps, glúteos"),
            e("stiff", "Stiff", "Barra na frente, pernas quase estendidas. Desça sentindo os posteriores.", "Isquiotibiais, glúteos, lombar"),
            e("extensora", "Cadeira Extensora", "Sentado, estenda as pernas até extensão total.", "Quadríceps"),
            e("flexora", "Cadeira Flexora", "Deitado, flexione os joelhos trazendo os pés ao glúteo.", "Isquiotibiais"),
            e("afundo bulgaro", "Afundo Búlgaro", "Pé traseiro elevado no banco. Desça até 90° no joelho da frente.", "Quadríceps, glúteos"),
            e("desenvolvimento", "Desenvolvimento", "Sentado, halteres nos ombros, empurre até estender.", "Deltóide, tríceps"),
            e("elevacao lateral", "Elevação Lateral", "Em pé, eleve halteres lateralmente até os ombros.", "Deltóide lateral"),
            e("face pull", "Face Pull", "Polia alta com corda, puxe em direção ao rosto.", "Deltóide posterior, trapézio"),
            e("rosca direta", "Rosca Direta", "Pegada supinada, flexione cotovelos sem mover o tronco.", "Bíceps"),
            e("triceps corda", "Tríceps Corda", "Polia alta, cotovelos fixos, estenda abrindo a corda.", "Tríceps"),
            e("paralela", "Paralelas (Dips)", "Barras paralelas, corpo inclinado. Desça flexionando cotovelos.", "Tríceps, peitoral inferior"),
            e("prancha", "Prancha", "Antebraços e pontas dos pés, corpo reto. Core contraído.", "Reto abdominal, transverso"),
            e("burpee", "Burpee", "Agache, mãos no chão, pés para trás, flexão, volte e salte.", "Corpo todo"),
            e("muscle up", "Muscle Up", "Barra fixa: puxe explosivo e faça a transição para cima.", "Dorsal, peitoral, tríceps (avançado)"),
            e("handstand", "Parada de Mão", "Mãos no chão, corpo invertido. Treine contra a parede.", "Ombros, core, equilíbrio"),
            e("kettlebell swing", "Kettlebell Swing", "Balance o kettlebell entre as pernas até os ombros.", "Posterior, core, ombros"),
            e("pular corda", "Pular Corda", "Saltos leves na ponta dos pés, corda girando pelos pulsos.", "Cardio, panturrilhas")
    );

    private static Map.Entry<String, CuratedExercise> e(String key, String name, String desc, String muscles) {
        return Map.entry(key, new CuratedExercise(name, desc, muscles));
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
