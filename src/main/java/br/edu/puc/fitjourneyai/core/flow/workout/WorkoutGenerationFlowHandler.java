package br.edu.puc.fitjourneyai.core.flow.workout;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutGroup;
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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                appendWorkoutFooter(treinoComVideos),
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

        // Remove artefatos Markdown que a IA pode gerar (**bold**, __underline__)
        treino = treino.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1");

        String[] lines = treino.split("\n");
        StringBuilder enriched = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            enriched.append(line).append("\n");

            String trimmed = line.trim();
            String nextLine = i + 1 < lines.length ? lines[i + 1].trim() : "";
            if (isExerciseLine(trimmed) && !hasVideoNearby(trimmed, nextLine)) {
                String exerciseName = extractExerciseNameFromLine(trimmed);
                if (exerciseName != null && isLikelyExercise(exerciseName)) {
                    String url = "https://www.youtube.com/results?search_query="
                            + URLEncoder.encode(exerciseName + " execução correta", StandardCharsets.UTF_8);
                    enriched.append("   \uD83C\uDFA5 Vídeo: <a href=\"").append(url).append("\">ver execução</a>\n");
                }
            }
        }

        return enriched.toString().trim();
    }

    private boolean isExerciseLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String clean = line.replaceAll("<[^>]+>", "")
                .replaceAll("\\*+", "")
                .trim();

        return clean.matches("^\\d{1,2}[.)]\\s+.+")
                || clean.matches("^\\d{1,2}\\s+-\\s+.+")
                || clean.matches("^[-•]\\s+[A-ZÀ-Ý].+");
    }

    private boolean hasVideoNearby(String currentLine, String nextLine) {
        String current = currentLine == null ? "" : currentLine.toLowerCase();
        String next = nextLine == null ? "" : nextLine.toLowerCase();
        return current.contains("youtube.com") || current.contains("vídeo:") || current.contains("video:")
                || next.contains("youtube.com") || next.contains("vídeo:") || next.contains("video:");
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
                || lower.startsWith("execute") || lower.startsWith("repita")
                || lower.startsWith("objetivo") || lower.startsWith("nível") || lower.startsWith("nivel")
                || lower.startsWith("duração") || lower.startsWith("duracao") || lower.startsWith("intensidade")
                || lower.equals("aquecimento") || lower.equals("treino principal")
                || lower.equals("finalização") || lower.equals("finalizacao") || lower.equals("alongamento")) {
            return false;
        }
        return true;
    }

    private String extractExerciseNameFromLine(String line) {
        String cleaned = line.replaceAll("<[^>]+>", "")
                .replaceFirst("^\\d+[.)\\-]\\s+", "")
                .replaceFirst("^\\d+\\s+-\\s+", "")
                .replaceFirst("^[-•]\\s+", "")
                .replaceAll("\\*+", "")
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
        String sugestao = shouldHideHistory(ultimosTreinos)
                ? ""
                : "\n\nHistórico recente: " + ultimosTreinos;

        return FlowResult.text(
                "O que você quer treinar hoje?" + sugestao + """
                        
                        
                        Me manda do seu jeito que eu ajusto foco, volume e duração.
                        Ex: "costas e bíceps em 60 min", "pernas pesado", "corrida leve 30 min".""",
                ConversationFlowType.WORKOUT_GENERATION,
                STEP_ASK_WHAT,
                Map.of(),
                null
        );
    }

    private String appendWorkoutFooter(String treino) {
        String cleaned = treino == null ? "" : treino.trim();
        String lower = cleaned.toLowerCase();

        String registerLine = lower.contains("/treino_feito")
                ? ""
                : "\n\nQuando terminar, mande /treino_feito para eu registrar esse treino sem retrabalho.";

        return cleaned + registerLine + "\n\nAgora executa com técnica, foco e constância. Hoje conta.";
    }

    // ========================================================================
    // CONTEXTO E HISTÓRICO
    // ========================================================================

    private Map<String, String> buildAiContext(User user, String pedido) {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("pedido", pedido);
        ctx.put("grupoMuscular", pedido);
        ctx.put("ultimosTreinos", getRecentWorkoutsSummary(user));

        Integer requestedDuration = extractRequestedDurationMinutes(pedido);
        if (requestedDuration != null) {
            ctx.put("duracaoSolicitadaMinutos", requestedDuration.toString());
            ctx.put("duracaoSolicitadaLabel", formatDuration(requestedDuration));
        }

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
                    .sorted(Comparator.comparing(
                            Workout::getDataRealizacao,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .limit(4)
                    .map(this::formatHistoryItem)
                    .distinct()
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            log.warn("Erro ao buscar treinos recentes: {}", e.getMessage());
            return "sem histórico recente";
        }
    }

    private boolean shouldHideHistory(String summary) {
        if (summary == null || summary.isBlank()) {
            return true;
        }
        String lower = summary.toLowerCase();
        return lower.startsWith("sem ") || lower.startsWith("primeiro ");
    }

    private String formatHistoryItem(Workout workout) {
        String label = extractWorkoutLabel(workout);
        String date = workout.getDataRealizacao() != null
                ? workout.getDataRealizacao().format(DateTimeFormatter.ofPattern("dd/MM"))
                : "";
        String duration = workout.getDuracaoMinutos() != null
                ? " - " + workout.getDuracaoMinutos() + "min"
                : "";

        if (date.isBlank()) {
            return label + duration;
        }
        return label + duration + " em " + date;
    }

    private String extractWorkoutLabel(Workout workout) {
        if (workout == null) {
            return "Treino";
        }

        String fromObservation = extractPedidoFromObservation(workout.getObservacoes());
        if (fromObservation != null) {
            return fromObservation;
        }

        if (workout.getGrupoMuscular() != null) {
            return formatGroupName(workout.getGrupoMuscular());
        }

        String fromDescription = extractTreinoHeader(workout.getDescricaoTreino());
        if (fromDescription != null) {
            return fromDescription;
        }

        return "Treino";
    }

    private String extractPedidoFromObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(?i)pedido:\\s*([^|\\n]+)").matcher(observation);
        if (!matcher.find()) {
            return null;
        }
        return truncateLabel(matcher.group(1).trim());
    }

    private String extractTreinoHeader(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(?im)^\\s*treino\\s*:\\s*(.+)$").matcher(description);
        if (matcher.find()) {
            return truncateLabel(matcher.group(1).trim());
        }

        return description.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.length() <= 80)
                .findFirst()
                .map(this::truncateLabel)
                .orElse(null);
    }

    private String truncateLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Treino";
        }
        String cleaned = label.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 45 ? cleaned.substring(0, 42).trim() + "..." : cleaned;
    }

    private String formatGroupName(WorkoutGroup group) {
        return switch (group) {
            case PEITO -> "Peito";
            case COSTAS -> "Costas";
            case PERNAS -> "Pernas";
            case OMBRO -> "Ombro";
            case BRACOS -> "Braços";
            case ABDOMEN -> "Abdômen";
            case FULLBODY -> "Full Body";
            case CARDIO -> "Cardio";
            case CORRIDA -> "Corrida";
            case OUTRO -> "Treino geral";
        };
    }

    // ========================================================================
    // PERSISTÊNCIA
    // ========================================================================

    private void persistGeneratedWorkout(User user, String pedido, String treinoGerado) {
        try {
            Integer duration = extractEstimatedDurationMinutes(treinoGerado);
            if (duration == null) {
                duration = extractRequestedDurationMinutes(pedido);
            }

            Workout workout = Workout.builder()
                    .user(user)
                    .grupoMuscular(mapPedidoToWorkoutGroup(pedido))
                    .fonte(WorkoutSource.IA)
                    .descricaoTreino(treinoGerado)
                    .dataGeracao(LocalDateTime.now())
                    .duracaoMinutos(duration)
                    .observacoes("Pedido: " + normalizeWorkoutRequestLabel(pedido))
                    .build();

            workoutRepository.save(workout);
            log.info("Treino IA persistido: user={}", user.getId());
        } catch (Exception e) {
            log.error("Erro ao persistir treino gerado para user={}: {}", user.getId(), e.getMessage());
            // Não falha o fluxo — o treino já foi entregue ao usuário
        }
    }

    private WorkoutGroup mapPedidoToWorkoutGroup(String texto) {
        if (texto == null || texto.isBlank()) {
            return WorkoutGroup.OUTRO;
        }

        String lower = texto.toLowerCase();
        if (lower.contains("peito") || lower.contains("peitoral") || lower.contains("supino")) {
            return WorkoutGroup.PEITO;
        }
        if (lower.contains("costa") || lower.contains("dorsal") || lower.contains("remada") || lower.contains("puxada")) {
            return WorkoutGroup.COSTAS;
        }
        if (lower.contains("perna") || lower.contains("quadríceps") || lower.contains("quadriceps")
                || lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("agachamento")) {
            return WorkoutGroup.PERNAS;
        }
        if (lower.contains("ombro") || lower.contains("deltoid") || lower.contains("desenvolvimento")) {
            return WorkoutGroup.OMBRO;
        }
        if (lower.contains("braço") || lower.contains("braco") || lower.contains("bíceps")
                || lower.contains("biceps") || lower.contains("tríceps") || lower.contains("triceps")) {
            return WorkoutGroup.BRACOS;
        }
        if (lower.contains("abdomen") || lower.contains("abdômen") || lower.contains("abdominal") || lower.contains("core")) {
            return WorkoutGroup.ABDOMEN;
        }
        if (lower.contains("fullbody") || lower.contains("full body") || lower.contains("corpo todo")) {
            return WorkoutGroup.FULLBODY;
        }
        if (lower.contains("corrida") || lower.contains("correr") || lower.contains("esteira")) {
            return WorkoutGroup.CORRIDA;
        }
        if (lower.contains("cardio") || lower.contains("hiit") || lower.contains("bike") || lower.contains("bicicleta")) {
            return WorkoutGroup.CARDIO;
        }
        return WorkoutGroup.OUTRO;
    }

    private String normalizeWorkoutRequestLabel(String text) {
        if (text == null || text.isBlank()) {
            return "treino sugerido";
        }

        String lower = text.toLowerCase();
        List<String> groups = new java.util.ArrayList<>();
        if (lower.contains("peito") || lower.contains("peitoral")) groups.add("Peito");
        if (lower.contains("costa") || lower.contains("dorsal")) groups.add("Costas");
        if (lower.contains("perna") || lower.contains("quadriceps") || lower.contains("quadríceps")) groups.add("Pernas");
        if (lower.contains("ombro") || lower.contains("deltoid")) groups.add("Ombro");
        if (lower.contains("triceps") || lower.contains("tríceps")) groups.add("Tríceps");
        if (lower.contains("biceps") || lower.contains("bíceps")) groups.add("Bíceps");
        if (lower.contains("abdomen") || lower.contains("abdômen") || lower.contains("abdominal")) groups.add("Abdômen");
        if (lower.contains("corrida") || lower.contains("correr") || lower.contains("5km") || lower.contains("10km")) groups.add("Corrida");
        if (lower.contains("cardio") || lower.contains("hiit")) groups.add("Cardio");
        if (lower.contains("fullbody") || lower.contains("full body") || lower.contains("corpo todo")) groups.add("Full Body");

        if (!groups.isEmpty()) {
            return String.join(" + ", groups);
        }

        String cleaned = text
                .replaceAll("(?i)\\b(me manda|manda|mande|quero|queria|monta|monte|gera|gere|faz|faça|um|uma|treino|treinao|treinão|de|para|pra|por favor|pfv)\\b", " ")
                .replaceAll("[,.;:!?]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return "treino sugerido";
        }
        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
    }

    private Integer extractEstimatedDurationMinutes(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile(
                "(?i)dura(?:ção|cao|\\.)?[^\\n\\d]{0,30}(\\d{2,3})(?:\\s*(?:-|a|até|–|—)\\s*(\\d{2,3}))?\\s*(?:min|minutos)"
        ).matcher(text);
        if (matcher.find()) {
            int first = Integer.parseInt(matcher.group(1));
            if (matcher.group(2) == null) {
                return first;
            }

            int second = Integer.parseInt(matcher.group(2));
            return Math.round((first + second) / 2.0f);
        }

        Matcher hourMatcher = Pattern.compile(
                "(?i)dura(?:ção|cao|\\.)?[^\\n\\d]{0,30}(\\d{1,2})(?:\\s*h|\\s*hora(?:s)?)\\s*(?:(\\d{1,2})\\s*(?:min|minutos)?)?"
        ).matcher(text);
        if (hourMatcher.find()) {
            int hours = Integer.parseInt(hourMatcher.group(1));
            int minutes = hourMatcher.group(2) != null ? Integer.parseInt(hourMatcher.group(2)) : 0;
            return hours * 60 + minutes;
        }

        return null;
    }

    private Integer extractRequestedDurationMinutes(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text.toLowerCase()
                .replace(",", ".")
                .replaceAll("\\s+", " ")
                .trim();

        Matcher compactHourMatcher = Pattern.compile("\\b(\\d{1,2})\\s*h\\s*(\\d{1,2})?\\b").matcher(normalized);
        if (compactHourMatcher.find()) {
            int hours = Integer.parseInt(compactHourMatcher.group(1));
            int minutes = compactHourMatcher.group(2) != null ? Integer.parseInt(compactHourMatcher.group(2)) : 0;
            return validDuration(hours * 60 + minutes);
        }

        Matcher hourMatcher = Pattern.compile(
                "\\b(\\d{1,2})\\s*(?:hora|horas|hr|hrs)\\b(?:\\s*(?:e)?\\s*(\\d{1,2})\\s*(?:min|minuto|minutos))?"
        ).matcher(normalized);
        if (hourMatcher.find()) {
            int hours = Integer.parseInt(hourMatcher.group(1));
            int minutes = hourMatcher.group(2) != null ? Integer.parseInt(hourMatcher.group(2)) : 0;
            return validDuration(hours * 60 + minutes);
        }

        Matcher minuteMatcher = Pattern.compile("\\b(\\d{2,3})\\s*(?:min|mins|minuto|minutos)\\b").matcher(normalized);
        if (minuteMatcher.find()) {
            return validDuration(Integer.parseInt(minuteMatcher.group(1)));
        }

        return null;
    }

    private Integer validDuration(int minutes) {
        return minutes >= 15 && minutes <= 240 ? minutes : null;
    }

    private String formatDuration(int minutes) {
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return minutes + " minutos (" + hours + (hours == 1 ? " hora" : " horas") + ")";
        }
        if (minutes > 60) {
            return minutes + " minutos (" + (minutes / 60) + "h" + (minutes % 60) + ")";
        }
        return minutes + " minutos";
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
