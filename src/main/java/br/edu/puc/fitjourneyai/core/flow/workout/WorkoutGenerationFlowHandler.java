package br.edu.puc.fitjourneyai.core.flow.workout;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutSource;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import br.edu.puc.fitjourneyai.infrastructure.ai.OpenAiServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fluxo 5 — Geração de Treino Personalizada com IA.
 * <p>
 * Gatilhos: comando /treino ou mensagens como "me manda um treino de peito".
 * <p>
 * Conforme Fig.10 do Pacote Consolidado:
 * <ol>
 *   <li>Recupera contexto do usuário (perfil + histórico recente)</li>
 *   <li>Interpreta pedido (grupo muscular, com correção de typos via IA)</li>
 *   <li>Se contexto insuficiente, pede complemento mínimo</li>
 *   <li>Gera treino via IA com prompt contextualizado</li>
 *   <li>Valida saída e oferece fallback determinístico</li>
 *   <li>Persiste treino sugerido (fonte=IA)</li>
 * </ol>
 * <p>
 * Fluxo de 2 passos:
 * <ul>
 *   <li>Step 1: Pergunta o que o usuário quer treinar (ou extrai do texto livre)</li>
 *   <li>Step 2: Gera e entrega o treino</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutGenerationFlowHandler implements FlowHandler {

    private static final int STEP_ASK_WHAT = 1;
    private static final int STEP_GENERATING = 2;

    private final AiService aiService;
    private final WorkoutRepository workoutRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.WORKOUT_GENERATION;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Antes de gerar um treino, preciso te conhecer melhor!\n\nUse /start para fazer o cadastro.",
                    "Use /start para iniciar o cadastro."
            );
        }

        Integer step = context.state().getCurrentStep();

        // Primeira entrada: tenta extrair pedido direto do texto
        if (step == null || !context.state().hasActiveFlow()) {
            return handleInitialEntry(context);
        }

        Map<String, String> partial = loadPartialData(context.state().getPartialData());

        return switch (step) {
            case STEP_ASK_WHAT -> handleWhatToTrain(context, partial);
            case STEP_GENERATING -> handleWhatToTrain(context, partial); // retry
            default -> handleInitialEntry(context);
        };
    }

    // ========================================================================
    // STEPS
    // ========================================================================

    /**
     * Entrada inicial: se o texto já contém o pedido (ex: "treino de pernas"),
     * extrai e gera direto. Se é só "/treino", pergunta o que quer treinar.
     */
    private FlowResult handleInitialEntry(FlowContext context) {
        String text = context.rawText();

        // Se é só o comando, pede detalhes
        if (text == null || text.trim().equalsIgnoreCase("/treino")) {
            return askWhatToTrain(context.user());
        }

        // Tenta extrair o pedido do texto livre (ex: "quero um treino de peito")
        String pedido = text.trim();
        // Remove prefixo de comando se presente
        if (pedido.toLowerCase().startsWith("/treino ")) {
            pedido = pedido.substring(8).trim();
        }

        if (pedido.length() < 2) {
            return askWhatToTrain(context.user());
        }

        // Tem pedido → gera direto
        return generateAndRespond(context.user(), pedido);
    }

    /**
     * O usuário respondeu com o que quer treinar.
     */
    private FlowResult handleWhatToTrain(FlowContext context, Map<String, String> partial) {
        if (!context.hasText()) {
            return askWhatToTrain(context.user());
        }

        String pedido = context.rawText().trim();

        // Normaliza typos via IA (ex: "p3ito" → "peito")
        String normalizado = normalizeWithAiFallback(pedido);
        if (!normalizado.equals(pedido)) {
            log.info("Typo normalizado: '{}' → '{}'", pedido, normalizado);
            pedido = normalizado;
        }

        return generateAndRespond(context.user(), pedido);
    }

    // ========================================================================
    // GERAÇÃO
    // ========================================================================

    private FlowResult generateAndRespond(User user, String pedido) {
        Map<String, String> aiContext = buildAiContext(user, pedido);

        log.info("Gerando treino: user={}, pedido='{}'", user.getId(), pedido);

        String treino = aiService.generateWorkout(user, aiContext);

        persistGeneratedWorkout(user, pedido, treino);

        // Enriquece com links de vídeo por exercício
        String treinoComVideos = enrichWithVideoLinks(treino);

        return FlowResult.done(
                treinoComVideos + "\n\nQuando fizer esse treino, use /treino_feito para registrar!",
                "Use /treino_feito para registrar quando fizer o treino, ou /treino para outro treino."
        );
    }

    /**
     * Enriquece o treino gerado pela IA com links de busca do YouTube
     * para cada exercício detectado. Detecta linhas tipo "1. Supino reto"
     * ou "- Flexão de braço" e adiciona link de vídeo.
     */
    private String enrichWithVideoLinks(String treino) {
        if (treino == null || treino.isBlank()) return treino;

        String[] lines = treino.split("\n");
        StringBuilder enriched = new StringBuilder();

        for (String line : lines) {
            enriched.append(line).append("\n");

            String trimmed = line.trim();
            // Só linhas numeradas (1. 2. 3.) ou bullets (- ) com maiúscula
            if (trimmed.matches("^\\d+\\.\\s+.+") || trimmed.matches("^-\\s+[A-Z\u00C0-\u00FF].+")) {
                String exerciseName = extractExerciseNameFromLine(trimmed);
                if (exerciseName != null && isLikelyExercise(exerciseName)) {
                    String url = "https://www.youtube.com/results?search_query="
                            + URLEncoder.encode(exerciseName + " execução correta", StandardCharsets.UTF_8);
                    enriched.append("   \uD83C\uDFA5 Vídeo: ").append(url).append("\n");
                }
            }
        }

        return enriched.toString().trim();
    }

    /**
     * Filtra nomes que provavelmente são exercícios vs dicas/frases genéricas.
     * Exercícios tendem a ser curtos (2-6 palavras). Frases longas são dicas.
     */
    private boolean isLikelyExercise(String name) {
        if (name.length() < 4 || name.length() > 60) return false;
        // Mais de 8 palavras provavelmente é uma frase/dica, não exercício
        long wordCount = name.chars().filter(c -> c == ' ').count() + 1;
        if (wordCount > 8) return false;
        // Se começa com verbo de dica, não é exercício
        String lower = name.toLowerCase();
        if (lower.startsWith("se ") || lower.startsWith("mantenha") || lower.startsWith("lembre")
                || lower.startsWith("faça") || lower.startsWith("não ") || lower.startsWith("evite")
                || lower.startsWith("descanse") || lower.startsWith("beba") || lower.startsWith("coma")
                || lower.startsWith("priorize") || lower.startsWith("aumente") || lower.startsWith("reduza")
                || lower.startsWith("execute") || lower.startsWith("repita")) {
            return false;
        }
        return true;
    }

    private String extractExerciseNameFromLine(String line) {
        String cleaned = line.replaceFirst("^\\d+\\.\\s+", "")
                .replaceFirst("^-\\s+", "")
                .replaceAll("\\*+", "")
                .replaceAll("<[^>]+>", "") // Remove tags HTML (<b>, <i>, etc)
                .trim();

        String[] seps = {" – ", " - ", " — ", ": ", " (", "\t"};
        for (String sep : seps) {
            int idx = cleaned.indexOf(sep);
            if (idx > 3) { cleaned = cleaned.substring(0, idx).trim(); break; }
        }

        return cleaned.length() >= 4 ? cleaned : null;
    }

    private FlowResult askWhatToTrain(User user) {
        String ultimosTreinos = getRecentWorkoutsSummary(user);
        String sugestao = ultimosTreinos.isEmpty()
                ? ""
                : "\n\nSeus últimos treinos: " + ultimosTreinos;

        return FlowResult.text(
                "O que você quer treinar hoje?" + sugestao + """
                        
                        
                        Me diz o grupo muscular ou tipo de treino:
                        Ex: pernas, peito e tríceps, costas, corrida, fullbody...""",
                ConversationFlowType.WORKOUT_GENERATION,
                STEP_ASK_WHAT,
                Map.of(),
                null
        );
    }

    // ========================================================================
    // CONTEXTO E HISTÓRICO
    // ========================================================================

    private Map<String, String> buildAiContext(User user, String pedido) {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("pedido", pedido);
        ctx.put("grupoMuscular", pedido);
        ctx.put("ultimosTreinos", getRecentWorkoutsSummary(user));
        return ctx;
    }

    /**
     * Busca os últimos 5 treinos do usuário para contexto (evitar repetição).
     */
    private String getRecentWorkoutsSummary(User user) {
        try {
            LocalDateTime umaSemanaAtras = LocalDateTime.now().minusDays(7);
            List<Workout> recentes = workoutRepository.findByUserAndDataRealizacaoBetween(
                    user, umaSemanaAtras, LocalDateTime.now());

            if (recentes.isEmpty()) {
                // Verifica se é usuário novo (nunca treinou) vs inativo
                LocalDateTime umMesAtras = LocalDateTime.now().minusDays(30);
                List<Workout> historico = workoutRepository.findByUserAndDataRealizacaoBetween(
                        user, umMesAtras, LocalDateTime.now());
                if (historico.isEmpty()) {
                    return "primeiro treino do usuário — não há histórico anterior";
                }
                return "sem treinos na última semana (mas tem histórico anterior)";
            }

            return recentes.stream()
                    .map(w -> {
                        String grupo = w.getDescricaoTreino() != null
                                ? w.getDescricaoTreino()
                                : (w.getGrupoMuscular() != null ? w.getGrupoMuscular().name() : "geral");
                        return grupo;
                    })
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            log.warn("Erro ao buscar treinos recentes: {}", e.getMessage());
            return "sem histórico recente";
        }
    }

    // ========================================================================
    // PERSISTÊNCIA
    // ========================================================================

    private void persistGeneratedWorkout(User user, String pedido, String treinoGerado) {
        try {
            Workout workout = Workout.builder()
                    .user(user)
                    .fonte(WorkoutSource.IA)
                    .descricaoTreino(treinoGerado)
                    .dataGeracao(LocalDateTime.now())
                    .observacoes("Pedido: " + pedido)
                    .build();

            workoutRepository.save(workout);
            log.info("Treino IA persistido: user={}", user.getId());
        } catch (Exception e) {
            log.error("Erro ao persistir treino gerado para user={}: {}", user.getId(), e.getMessage());
            // Não falha o fluxo — o treino já foi entregue ao usuário
        }
    }

    // ========================================================================
    // NORMALIZAÇÃO DE TYPOS
    // ========================================================================

    /**
     * Cadeia de normalização para typos em nomes de grupo muscular:
     * 1. Primeiro tenta similaridade determinística (barato e rápido)
     * 2. Se não resolver, usa IA como corretor (fallback)
     */
    private String normalizeWithAiFallback(String texto) {
        // 1. Tenta correção determinística por similaridade
        String deterministico = fuzzyMatch(texto);
        if (deterministico != null) {
            return deterministico;
        }

        // 2. Fallback: IA corrige
        if (aiService instanceof OpenAiServiceImpl openAi) {
            String normalizado = openAi.normalizeWorkoutGroup(texto);
            if (normalizado != null && !normalizado.equals(texto)) {
                return normalizado;
            }
        }

        return texto;
    }

    /**
     * Correção determinística por similaridade: trata typos comuns
     * usando distância de caracteres e padrões conhecidos.
     * Exemplos: "p3ito"→"peito", "perma"→"perna", "costta"→"costas"
     */
    private String fuzzyMatch(String texto) {
        if (texto == null || texto.isBlank()) return null;

        String lower = texto.toLowerCase().trim()
                .replaceAll("[0-9]", "")     // remove números acidentais (p3ito → pito)
                .replaceAll("(.)(\\1)+", "$1$2"); // reduz letras repetidas (costttas → costtas)

        // Mapa de variações comuns (typo → correto)
        Map<String, String> typoMap = Map.ofEntries(
                // peito
                Map.entry("pito", "peito"), Map.entry("peio", "peito"),
                Map.entry("peto", "peito"), Map.entry("pieto", "peito"),
                Map.entry("peitoo", "peito"), Map.entry("peitto", "peito"),
                // costas
                Map.entry("costa", "costas"), Map.entry("costta", "costas"),
                Map.entry("costtas", "costas"), Map.entry("cosstas", "costas"),
                // pernas
                Map.entry("perna", "pernas"), Map.entry("perma", "pernas"),
                Map.entry("permas", "pernas"), Map.entry("pernna", "pernas"),
                // ombro
                Map.entry("onbro", "ombro"), Map.entry("ombros", "ombro"),
                Map.entry("ombr", "ombro"),
                // braços
                Map.entry("braco", "braços"), Map.entry("bracos", "braços"),
                Map.entry("braço", "braços"),
                // abdômen
                Map.entry("abdomen", "abdômen"), Map.entry("abdomem", "abdômen"),
                Map.entry("abomen", "abdômen"), Map.entry("abd", "abdômen"),
                // corrida
                Map.entry("corida", "corrida"), Map.entry("corrda", "corrida"),
                Map.entry("correr", "corrida")
        );

        if (typoMap.containsKey(lower)) {
            return typoMap.get(lower);
        }

        // Verifica se é substring de algum termo válido
        List<String> termos = List.of("peito", "costas", "pernas", "ombro", "braços",
                "abdômen", "fullbody", "cardio", "corrida");

        for (String termo : termos) {
            // Se difere por no máximo 2 caracteres de tamanho e contém o início
            if (Math.abs(lower.length() - termo.length()) <= 2
                    && (lower.startsWith(termo.substring(0, Math.min(3, termo.length())))
                    || termo.startsWith(lower.substring(0, Math.min(3, lower.length()))))) {
                return termo;
            }
        }

        return null; // Não conseguiu corrigir deterministicamente
    }

    private Map<String, String> loadPartialData(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
}
