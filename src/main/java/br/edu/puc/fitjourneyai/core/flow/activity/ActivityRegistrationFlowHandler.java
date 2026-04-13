package br.edu.puc.fitjourneyai.core.flow.activity;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutGroup;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutSource;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluxo 4 — Registro Manual de Atividade Multimodal.
 * <p>
 * Gatilhos: comando /treino_feito ou mensagens como "terminei o treino de pernas".
 * <p>
 * Conduz 4 passos de coleta + confirmação:
 * <ol>
 *   <li>Grupo muscular / modalidade</li>
 *   <li>Duração em minutos</li>
 *   <li>Intensidade percebida (1-10)</li>
 *   <li>Observações / exercícios (opcional)</li>
 *   <li>Confirmação e persistência</li>
 * </ol>
 * <p>
 * Diferenças do MVP:
 * <ul>
 *   <li><b>Persiste de verdade</b> — o MVP tinha {@code // TODO: salvar Workout}</li>
 *   <li>Dados parciais em ConversationState.partialData (JSONB)</li>
 *   <li>Mapeamento determinístico de texto livre para {@link WorkoutGroup}</li>
 *   <li>Confirmação antes de salvar</li>
 *   <li>Registra data de realização</li>
 * </ul>
 * <p>
 * Conforme Fig.9 do Pacote Consolidado: coleta, validação determinística,
 * persistência com verificação e próxima ação sugerida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityRegistrationFlowHandler implements FlowHandler {

    private static final int STEP_GRUPO = 1;
    private static final int STEP_DURACAO = 2;
    private static final int STEP_INTENSIDADE = 3;
    private static final int STEP_OBSERVACOES = 4;
    private static final int STEP_CONFIRMACAO = 5;

    private final WorkoutRepository workoutRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.ACTIVITY_REGISTRATION;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        // Pré-checagem
        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Antes de registrar um treino, preciso te conhecer melhor!\n\nUse /start para fazer o cadastro.",
                    "Use /start para iniciar o cadastro."
            );
        }

        Integer step = context.state().getCurrentStep();

        // Primeira entrada → inicia o fluxo
        if (step == null || !context.state().hasActiveFlow()) {
            return startFlow();
        }

        Map<String, String> partial = loadPartialData(context.state().getPartialData());

        return switch (step) {
            case STEP_GRUPO -> handleGrupo(context, partial);
            case STEP_DURACAO -> handleDuracao(context, partial);
            case STEP_INTENSIDADE -> handleIntensidade(context, partial);
            case STEP_OBSERVACOES -> handleObservacoes(context, partial);
            case STEP_CONFIRMACAO -> handleConfirmacao(context, partial);
            default -> startFlow();
        };
    }

    // ========================================================================
    // STEP HANDLERS
    // ========================================================================

    private FlowResult startFlow() {
        return FlowResult.text(
                """
                Boa! \uD83D\uDCAA Vamos registrar um treino que você fez!
                
                Me conta: qual tipo de treino foi?
                
                Ex: peito e tríceps, costas, pernas, corrida, fullbody, cardio...""",
                ConversationFlowType.ACTIVITY_REGISTRATION,
                STEP_GRUPO,
                Map.of(),
                null
        );
    }

    private FlowResult handleGrupo(FlowContext context, Map<String, String> partial) {
        if (!context.hasText()) {
            return stayOnStep(STEP_GRUPO, partial,
                    "Hmm, não entendi qual treino você fez \uD83D\uDE05\n\nMe conta o tipo! Ex: peito, costas, pernas, corrida, fullbody...");
        }

        String textoOriginal = context.rawText().trim();
        String textoParaMap = textoOriginal;

        // Tenta mapeamento direto
        WorkoutGroup grupo = mapToWorkoutGroup(textoOriginal);

        // Se caiu em OUTRO, tenta normalizar typos antes de desistir
        if (grupo == WorkoutGroup.OUTRO) {
            String normalizado = fuzzyNormalizeGroup(textoOriginal);
            if (normalizado != null) {
                WorkoutGroup grupoCorrigido = mapToWorkoutGroup(normalizado);
                if (grupoCorrigido != WorkoutGroup.OUTRO) {
                    grupo = grupoCorrigido;
                    textoParaMap = normalizado;
                    log.info("Typo corrigido no grupo: '{}' → '{}'", textoOriginal, normalizado);
                }
            }
        }

        // Se ainda OUTRO, confirma com o usuário que é genérico
        if (grupo == WorkoutGroup.OUTRO) {
            partial.put("grupo", WorkoutGroup.OUTRO.name());
            partial.put("grupoTexto", textoOriginal);

            return FlowResult.text(
                    String.format("""
                            Não reconheci "%s" como um grupo muscular específico, mas tudo bem! \uD83D\uDE09
                            Vou registrar como treino geral.
                            
                            Aproximadamente quantos minutos durou esse treino?
                            (Ex: 45)""", textoOriginal),
                    ConversationFlowType.ACTIVITY_REGISTRATION,
                    STEP_DURACAO,
                    partial,
                    null
            );
        }

        partial.put("grupo", grupo.name());
        partial.put("grupoTexto", textoParaMap);

        String feedback = textoParaMap.equalsIgnoreCase(textoOriginal)
                ? String.format("Treino de %s — show! \uD83D\uDCAA", textoParaMap)
                : String.format("Entendi! Treino de %s \uD83D\uDCAA (corrigi de \"%s\")", textoParaMap, textoOriginal);

        return FlowResult.text(
                feedback + "\n\nAproximadamente quantos minutos durou esse treino?\n(Ex: 45)",
                ConversationFlowType.ACTIVITY_REGISTRATION,
                STEP_DURACAO,
                partial,
                null
        );
    }

    private FlowResult handleDuracao(FlowContext context, Map<String, String> partial) {
        Integer duracao = parseIntegerInRange(context.rawText(), 5, 300);
        if (duracao == null) {
            return stayOnStep(STEP_DURACAO, partial,
                    "Hmm, não consegui entender essa duração \uD83D\uDE05\n\nMe manda um número em minutos (entre 5 e 300).\nEx: 45");
        }

        partial.put("duracao", duracao.toString());

        return FlowResult.text(
                String.format("""
                        %d minutos — beleza! \u23F1\uFE0F
                        
                        Em uma escala de 1 a 10, como você avalia a intensidade desse treino?
                        (1 = bem leve, 10 = extremamente intenso)""", duracao),
                ConversationFlowType.ACTIVITY_REGISTRATION,
                STEP_INTENSIDADE,
                partial,
                null
        );
    }

    private FlowResult handleIntensidade(FlowContext context, Map<String, String> partial) {
        Integer intensidade = parseIntegerInRange(context.rawText(), 1, 10);
        if (intensidade == null) {
            return stayOnStep(STEP_INTENSIDADE, partial,
                    "Preciso de um número de 1 a 10 pra intensidade \uD83D\uDE05");
        }

        partial.put("intensidade", intensidade.toString());

        String emoji = intensidade >= 8 ? "\uD83D\uDD25" : intensidade >= 5 ? "\uD83D\uDCAA" : "\uD83D\uDE0A";

        return FlowResult.text(
                String.format("""
                        Intensidade %d/10 %s
                        
                        Quer anotar quais exercícios fez? Isso ajuda a gente a acompanhar melhor!
                        (manda "pular" se preferir)""", intensidade, emoji),
                ConversationFlowType.ACTIVITY_REGISTRATION,
                STEP_OBSERVACOES,
                partial,
                null
        );
    }

    private FlowResult handleObservacoes(FlowContext context, Map<String, String> partial) {
        String text = context.normalizedText();

        if (!isSkip(text) && context.hasText()) {
            partial.put("observacoes", context.rawText().trim());
        }

        String resumo = buildResumo(partial);

        return FlowResult.text(
                resumo + "\n\nTudo certo? Posso registrar? (\"sim\" ou \"não\") \uD83D\uDE09",
                ConversationFlowType.ACTIVITY_REGISTRATION,
                STEP_CONFIRMACAO,
                partial,
                null
        );
    }

    private FlowResult handleConfirmacao(FlowContext context, Map<String, String> partial) {
        String lower = context.normalizedText();

        if (lower.startsWith("sim") || lower.equals("s") || lower.equals("ok")
                || lower.equals("salvar") || lower.equals("confirmar")) {
            return persistWorkout(context.user(), partial);
        }

        if (lower.startsWith("nao") || lower.startsWith("não") || lower.equals("n")
                || lower.equals("refazer")) {
            return FlowResult.text(
                    "Sem problema! \uD83D\uDE09 Vamos refazer.\n\nQual tipo de treino você fez?",
                    ConversationFlowType.ACTIVITY_REGISTRATION,
                    STEP_GRUPO,
                    Map.of(),
                    null
            );
        }

        return stayOnStep(STEP_CONFIRMACAO, partial,
                "Me manda \"sim\" pra registrar ou \"não\" pra refazer \uD83D\uDE09");
    }

    // ========================================================================
    // PERSISTÊNCIA
    // ========================================================================

    private FlowResult persistWorkout(User user, Map<String, String> partial) {
        try {
            WorkoutGroup grupo = WorkoutGroup.valueOf(partial.get("grupo"));
            Integer duracao = partial.containsKey("duracao")
                    ? Integer.parseInt(partial.get("duracao")) : null;
            Integer intensidade = partial.containsKey("intensidade")
                    ? Integer.parseInt(partial.get("intensidade")) : null;
            String observacoes = partial.getOrDefault("observacoes", null);
            String grupoTexto = partial.getOrDefault("grupoTexto", grupo.name());

            Workout workout = Workout.builder()
                    .user(user)
                    .grupoMuscular(grupo)
                    .fonte(WorkoutSource.MANUAL)
                    .descricaoTreino(grupoTexto)
                    .dataRealizacao(LocalDateTime.now())
                    .duracaoMinutos(duracao)
                    .intensidadePercebida(intensidade)
                    .observacoes(observacoes)
                    .build();

            workoutRepository.save(workout);

            log.info("Treino registrado: user={}, grupo={}, duracao={}min, intensidade={}/10",
                    user.getId(), grupo, duracao, intensidade);

            String dataFormatada = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));

            return FlowResult.done(
                    String.format("""
                            Treino registrado com sucesso! \uD83C\uDF89
                            
                            \uD83D\uDCC5 %s
                            \uD83C\uDFCB\uFE0F Tipo: %s
                            \u23F1\uFE0F Duração: %s
                            \uD83D\uDCAA Intensidade: %s
                            \uD83D\uDCDD Observações: %s
                            
                            Mandou bem demais! \uD83D\uDD25 Esse treino já tá no seu histórico e vai aparecer nos gráficos de progresso.""",
                            dataFormatada,
                            grupoTexto,
                            duracao != null ? duracao + " min" : "-",
                            intensidade != null ? intensidade + "/10" : "-",
                            observacoes != null && !observacoes.isBlank() ? observacoes : "-"),
                    "Use /progresso para ver sua evolução ou /treino para pedir um novo treino!"
            );

        } catch (Exception e) {
            log.error("Erro ao persistir treino para user={}: {}", user.getId(), e.getMessage(), e);
            return FlowResult.done(
                    "Ops, tive um probleminha ao salvar seu treino \uD83D\uDE15 Tenta de novo com /treino_feito!",
                    "Use /treino_feito para tentar novamente."
            );
        }
    }

    // ========================================================================
    // UTILITÁRIOS
    // ========================================================================

    /**
     * Normalização determinística de typos em nomes de grupo muscular.
     * Remove números acidentais, reduz letras repetidas, e busca match
     * por prefixo ou variações conhecidas.
     *
     * @return texto corrigido, ou null se não conseguir corrigir
     */
    private String fuzzyNormalizeGroup(String texto) {
        if (texto == null || texto.isBlank()) return null;

        String lower = texto.toLowerCase().trim()
                .replaceAll("[0-9]", "")          // remove números (p3ito → pito)
                .replaceAll("(.)\\1{2,}", "$1$1") // reduz 3+ letras repetidas (costttas → costtas)
                .trim();

        if (lower.isEmpty()) return null;

        // Mapa de typos conhecidos → termo correto
        java.util.Map<String, String> typoMap = java.util.Map.ofEntries(
                // peito
                java.util.Map.entry("pito", "peito"), java.util.Map.entry("peio", "peito"),
                java.util.Map.entry("peto", "peito"), java.util.Map.entry("pieto", "peito"),
                java.util.Map.entry("peitoo", "peito"), java.util.Map.entry("peitto", "peito"),
                // costas
                java.util.Map.entry("costa", "costas"), java.util.Map.entry("costta", "costas"),
                java.util.Map.entry("costtas", "costas"), java.util.Map.entry("cosstas", "costas"),
                // pernas
                java.util.Map.entry("perna", "pernas"), java.util.Map.entry("perma", "pernas"),
                java.util.Map.entry("permas", "pernas"), java.util.Map.entry("pernna", "pernas"),
                java.util.Map.entry("prna", "pernas"), java.util.Map.entry("prnas", "pernas"),
                // ombro
                java.util.Map.entry("onbro", "ombro"), java.util.Map.entry("ombros", "ombro"),
                java.util.Map.entry("ombr", "ombro"), java.util.Map.entry("hombro", "ombro"),
                // braços
                java.util.Map.entry("braco", "braços"), java.util.Map.entry("bracos", "braços"),
                java.util.Map.entry("braço", "braços"),
                // abdômen
                java.util.Map.entry("abdomen", "abdômen"), java.util.Map.entry("abdomem", "abdômen"),
                java.util.Map.entry("abomen", "abdômen"), java.util.Map.entry("abd", "abdômen"),
                // corrida
                java.util.Map.entry("corida", "corrida"), java.util.Map.entry("corrda", "corrida"),
                java.util.Map.entry("correr", "corrida"),
                // cardio
                java.util.Map.entry("cadio", "cardio"), java.util.Map.entry("cardoo", "cardio")
        );

        if (typoMap.containsKey(lower)) {
            return typoMap.get(lower);
        }

        // Match por prefixo (3+ chars)
        java.util.List<String> termos = java.util.List.of(
                "peito", "costas", "pernas", "ombro", "braços", "abdômen",
                "fullbody", "cardio", "corrida", "caminhada", "natação");

        for (String termo : termos) {
            int prefixLen = Math.min(3, Math.min(lower.length(), termo.length()));
            if (prefixLen >= 3 && lower.substring(0, prefixLen).equals(termo.substring(0, prefixLen))
                    && Math.abs(lower.length() - termo.length()) <= 2) {
                return termo;
            }
        }

        return null;
    }

    /**
     * Mapeia texto livre do usuário para um WorkoutGroup.
     * Mapeamento determinístico — IA não decide, o código decide.
     */
    private WorkoutGroup mapToWorkoutGroup(String texto) {
        if (texto == null || texto.isBlank()) return WorkoutGroup.OUTRO;

        String lower = texto.toLowerCase().trim();

        if (lower.contains("peito") || lower.contains("peitoral") || lower.contains("chest")
                || lower.contains("supino")) {
            return WorkoutGroup.PEITO;
        }
        if (lower.contains("costa") || lower.contains("dorsal") || lower.contains("back")
                || lower.contains("remada") || lower.contains("puxada")) {
            return WorkoutGroup.COSTAS;
        }
        if (lower.contains("perna") || lower.contains("leg") || lower.contains("quadríceps")
                || lower.contains("quadriceps") || lower.contains("agachamento")
                || lower.contains("posterior") || lower.contains("glúteo")
                || lower.contains("gluteo")) {
            return WorkoutGroup.PERNAS;
        }
        if (lower.contains("ombro") || lower.contains("deltoid") || lower.contains("shoulder")
                || lower.contains("desenvolvimento")) {
            return WorkoutGroup.OMBRO;
        }
        if (lower.contains("braço") || lower.contains("braco") || lower.contains("bíceps")
                || lower.contains("biceps") || lower.contains("tríceps")
                || lower.contains("triceps") || lower.contains("arm")) {
            return WorkoutGroup.BRACOS;
        }
        if (lower.contains("abdomen") || lower.contains("abdômen") || lower.contains("abdominal")
                || lower.contains("core") || lower.contains("prancha")) {
            return WorkoutGroup.ABDOMEN;
        }
        if (lower.contains("fullbody") || lower.contains("full body") || lower.contains("corpo todo")
                || lower.contains("completo") || lower.contains("geral")) {
            return WorkoutGroup.FULLBODY;
        }
        if (lower.contains("cardio") || lower.contains("aeróbico") || lower.contains("aerobico")
                || lower.contains("hiit") || lower.contains("elíptico")
                || lower.contains("eliptico") || lower.contains("bicicleta")
                || lower.contains("bike") || lower.contains("natação")
                || lower.contains("natacao") || lower.contains("pular corda")) {
            return WorkoutGroup.CARDIO;
        }
        if (lower.contains("corrida") || lower.contains("correr") || lower.contains("run")
                || lower.contains("cooper") || lower.contains("esteira")
                || lower.contains("caminhada") || lower.contains("caminhar")) {
            return WorkoutGroup.CORRIDA;
        }

        return WorkoutGroup.OUTRO;
    }

    private String buildResumo(Map<String, String> partial) {
        String grupo = partial.getOrDefault("grupoTexto", "-");
        String duracao = partial.containsKey("duracao") ? partial.get("duracao") + " min" : "-";
        String intensidade = partial.containsKey("intensidade") ? partial.get("intensidade") + "/10" : "-";
        String obs = partial.getOrDefault("observacoes", "-");

        return String.format("""
                \uD83D\uDCCB Resumo do treino:
                
                \uD83C\uDFCB\uFE0F Tipo: %s
                \u23F1\uFE0F Duração: %s
                \uD83D\uDCAA Intensidade: %s
                \uD83D\uDCDD Observações: %s""", grupo, duracao, intensidade, obs);
    }

    private FlowResult stayOnStep(int step, Map<String, String> partial, String message) {
        return FlowResult.text(message,
                ConversationFlowType.ACTIVITY_REGISTRATION, step, partial, null);
    }

    private boolean isSkip(String text) {
        return text != null && (text.equals("pular") || text.equals("pula")
                || text.equals("skip") || text.equals("p") || text.equals("-"));
    }

    private Integer parseIntegerInRange(String text, int min, int max) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim().replaceAll("[^0-9]", "");
            if (cleaned.isEmpty()) return null;
            int value = Integer.parseInt(cleaned);
            return (value >= min && value <= max) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> loadPartialData(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Erro ao deserializar partialData: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
