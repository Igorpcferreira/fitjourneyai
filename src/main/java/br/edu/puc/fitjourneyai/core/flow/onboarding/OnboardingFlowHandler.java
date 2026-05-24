package br.edu.puc.fitjourneyai.core.flow.onboarding;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.GoalType;
import br.edu.puc.fitjourneyai.core.model.enums.LevelType;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fluxo 1 — Onboarding e Contextualização Inicial.
 * <p>
 * Conduz a coleta de dados em 7 passos:
 * <ol>
 *   <li>Nome</li>
 *   <li>Objetivo (GoalType)</li>
 *   <li>Nível de experiência (LevelType)</li>
 *   <li>Frequência semanal de treino</li>
 *   <li>Peso atual (obrigatório)</li>
 *   <li>Altura (opcional)</li>
 *   <li>Confirmação</li>
 * </ol>
 * <p>
 * Dados parciais são persistidos em ConversationState.partialData (JSONB),
 * substituindo o ConcurrentHashMap em memória do MVP.
 * Só persiste no User após confirmação no step 7.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingFlowHandler implements FlowHandler {

    private static final int STEP_NOME = 1;
    private static final int STEP_OBJETIVO = 2;
    private static final int STEP_NIVEL = 3;
    private static final int STEP_FREQ = 4;
    private static final int STEP_PESO = 5;
    private static final int STEP_ALTURA = 6;
    private static final int STEP_CONFIRMACAO = 7;

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.ONBOARDING;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        // Se já completou onboarding, redireciona para menu
        if (user.isOnboardingConcluido()) {
            return FlowResult.done(
                    String.format("Oi, %s! Você já está cadastrado. Use /menu para ver as opções.",
                            safe(user.getNome())),
                    "Use /menu para ver as opções."
            );
        }

        Integer step = context.state().getCurrentStep();

        // Primeira entrada ou step inválido → inicia no step 1
        if (step == null || step < STEP_NOME || step > STEP_CONFIRMACAO) {
            return startOnboarding();
        }

        // Carrega dados parciais do JSONB
        Map<String, String> partial = loadPartialData(context.state().getPartialData());

        // ===== DETECTA INTENÇÃO DE VOLTAR/CORRIGIR =====
        String backResult = detectBackCorrection(context.rawText(), step, partial);
        if (backResult != null) {
            return handleBackCorrection(backResult, partial);
        }

        return switch (step) {
            case STEP_NOME -> handleNome(context, partial);
            case STEP_OBJETIVO -> handleObjetivo(context, partial);
            case STEP_NIVEL -> handleNivel(context, partial);
            case STEP_FREQ -> handleFrequencia(context, partial);
            case STEP_PESO -> handlePeso(context, partial);
            case STEP_ALTURA -> handleAltura(context, partial);
            case STEP_CONFIRMACAO -> handleConfirmacao(context, partial);
            default -> startOnboarding();
        };
    }

    // ========================================================================
    // STEP HANDLERS
    // ========================================================================

    private FlowResult startOnboarding() {
        return FlowResult.text(
                """
                Oi! 😄 Eu sou o FitJourneyAI, seu parceiro de treinos e evolução física!
                
                Que legal ter você por aqui! Vamos montar seu perfil rapidinho para eu poder te ajudar da melhor forma possível.
                
                Para começar, me conta: como você gosta de ser chamado? 😉""",
                ConversationFlowType.ONBOARDING,
                STEP_NOME,
                Map.of(),
                null
        );
    }

    private FlowResult handleNome(FlowContext context, Map<String, String> partial) {
        if (!context.hasText()) {
            return FlowResult.text(
                    "Ops, não consegui captar seu nome \uD83D\uDE05 Me manda como você gosta de ser chamado!",
                    ConversationFlowType.ONBOARDING, STEP_NOME, partial, null
            );
        }

        String nome = extractPreferredName(context.rawText());
        if (nome == null || nome.isBlank()) {
            return FlowResult.text(
                    "Ops, não consegui captar seu nome 😅 Me manda só o nome ou algo como \"pode me chamar de Igor\".",
                    ConversationFlowType.ONBOARDING, STEP_NOME, partial, null
            );
        }

        partial.put("nome", nome);
        return FlowResult.text(
                String.format("""
                        Prazer, %s! \uD83D\uDE4C Que bom te conhecer!
                        
                        Agora me conta: qual é o seu objetivo principal?
                        Pode escolher mais de um separando por vírgula!
                        
                        1 - Emagrecer \uD83D\uDD25
                        2 - Ganhar massa muscular \uD83D\uDCAA
                        3 - Melhorar condicionamento \uD83C\uDFC3
                        4 - Correr 5km / 10km \uD83C\uDFC5
                        5 - Saúde e bem-estar geral \uD83C\uDF3F
                        6 - Ganhar força \uD83E\uDDBE
                        
                        Ex: "1 e 3" ou "2,6" ou só "2\"""", nome),
                ConversationFlowType.ONBOARDING,
                STEP_OBJETIVO,
                partial,
                null
        );
    }

    private FlowResult handleObjetivo(FlowContext context, Map<String, String> partial) {
        String input = context.rawText();
        List<GoalType> goals = parseMultipleGoals(input);

        if (goals.isEmpty()) {
            return FlowResult.text(
                    """
                    Hmm, não consegui entender \uD83E\uDD14
                    
                    Me manda o número (ou mais de um separado por vírgula):
                    1 - Emagrecer \uD83D\uDD25
                    2 - Ganhar massa muscular \uD83D\uDCAA
                    3 - Melhorar condicionamento \uD83C\uDFC3
                    4 - Correr 5km / 10km \uD83C\uDFC5
                    5 - Saúde e bem-estar geral \uD83C\uDF3F
                    6 - Ganhar força \uD83E\uDDBE
                    
                    Ex: "1 e 3" ou "2,5" ou só "2\"""",
                    ConversationFlowType.ONBOARDING, STEP_OBJETIVO, partial, null
            );
        }

        // Objetivo principal = primeiro da lista; demais ficam como contexto
        GoalType principal = goals.get(0);
        partial.put("objetivo", principal.name());

        if (goals.size() > 1) {
            String secundarios = goals.stream().skip(1)
                    .map(GoalType::getLabel)
                    .collect(Collectors.joining(", "));
            partial.put("objetivosSecundarios", secundarios);
        }

        String goalsLabel = goals.stream().map(GoalType::getLabel).collect(Collectors.joining(" + "));

        return FlowResult.text(
                String.format("""
                        Boa escolha! \uD83C\uDFAF Objetivo: %s
                        
                        E como você se classifica em termos de experiência com exercícios?
                        
                        1 - Iniciante (tô começando agora!) \uD83C\uDF31
                        2 - Intermediário (já tenho alguma prática) \uD83D\uDCAA
                        3 - Avançado (treino há bastante tempo) \uD83D\uDD25""", goalsLabel),
                ConversationFlowType.ONBOARDING,
                STEP_NIVEL,
                partial,
                null
        );
    }

    private FlowResult handleNivel(FlowContext context, Map<String, String> partial) {
        LevelType level = LevelType.fromUserInput(context.rawText());
        if (level == null) {
            return FlowResult.text(
                    """
                    Não entendi esse nível \uD83D\uDE05
                    
                    Me manda o número:
                    1 - Iniciante \uD83C\uDF31
                    2 - Intermediário \uD83D\uDCAA
                    3 - Avançado \uD83D\uDD25""",
                    ConversationFlowType.ONBOARDING, STEP_NIVEL, partial, null
            );
        }

        partial.put("nivel", level.name());
        return FlowResult.text(
                String.format("""
                        Nível: %s \u2705
                        
                        Quantos dias por semana você pretende treinar? (1 a 7)
                        
                        Não se preocupe, a gente pode ajustar isso depois! \uD83D\uDE09""", level.getLabel()),
                ConversationFlowType.ONBOARDING,
                STEP_FREQ,
                partial,
                null
        );
    }

    private FlowResult handleFrequencia(FlowContext context, Map<String, String> partial) {
        Integer freq = parseIntegerInRange(context.rawText(), 1, 7);
        if (freq == null) {
            return FlowResult.text(
                    "Preciso de um número entre 1 e 7 \uD83D\uDE05\n\nQuantos dias por semana você planeja treinar?",
                    ConversationFlowType.ONBOARDING, STEP_FREQ, partial, null
            );
        }

        partial.put("frequencia", freq.toString());
        return FlowResult.text(
                String.format("""
                        %dx por semana — ótimo ritmo! \uD83D\uDCAA
                        
                        Agora me diz: qual o seu peso atual em kg? (obrigatório)
                        Pode mandar tipo: 72 ou 72.5 ou 72,5""", freq),
                ConversationFlowType.ONBOARDING,
                STEP_PESO,
                partial,
                null
        );
    }

    private FlowResult handlePeso(FlowContext context, Map<String, String> partial) {
        Double peso = parseWeight(context.rawText());
        if (peso == null) {
            return FlowResult.text(
                    """
                    Hmm, não consegui entender esse peso \uD83D\uDE05
                    
                    Me manda algo como: 72 ou 72.5 ou 72,5""",
                    ConversationFlowType.ONBOARDING, STEP_PESO, partial, null
            );
        }

        partial.put("peso", peso.toString());
        return FlowResult.text(
                String.format("""
                        Peso: %.1f kg \u2705
                        
                        E qual a sua altura em cm? (opcional — manda "pular" se preferir)
                        Pode mandar: 175 ou 175.5 ou 175,5""", peso),
                ConversationFlowType.ONBOARDING,
                STEP_ALTURA,
                partial,
                null
        );
    }

    private FlowResult handleAltura(FlowContext context, Map<String, String> partial) {
        String lower = context.normalizedText();

        if (!lower.equals("pular") && !lower.equals("pula") && !lower.isBlank()) {
            Integer altura = parseHeightCm(context.rawText());
            if (altura == null) {
                return FlowResult.text(
                        """
                        Não entendi a altura \uD83D\uDE05 Me manda em cm (ex: 175 ou 175,5) ou "pular".""",
                        ConversationFlowType.ONBOARDING, STEP_ALTURA, partial, null
                );
            }
            partial.put("altura", altura.toString());
        }

        // Monta resumo para confirmação
        String resumo = buildResumo(partial);
        return FlowResult.text(
                resumo + "\n\nTudo certo? Posso salvar? (responda \"sim\" ou \"não\") \uD83D\uDE09",
                ConversationFlowType.ONBOARDING,
                STEP_CONFIRMACAO,
                partial,
                null
        );
    }

    private FlowResult handleConfirmacao(FlowContext context, Map<String, String> partial) {
        String lower = context.normalizedText();

        if (lower.startsWith("sim") || lower.equals("s") || lower.equals("ok")
                || lower.equals("confirmar") || lower.equals("salvar")
                || lower.equals("pode") || lower.equals("claro") || lower.equals("bora")
                || lower.equals("isso") || lower.equals("certo") || lower.equals("beleza")
                || lower.startsWith("pode sim") || lower.startsWith("isso mesmo")
                || lower.startsWith("ta certo") || lower.startsWith("ta bom")) {

            User user = context.user();
            user.setNome(partial.get("nome"));
            user.setObjetivo(GoalType.valueOf(partial.get("objetivo")));
            user.setNivel(LevelType.valueOf(partial.get("nivel")));
            user.setFrequenciaTreinoEstimada(Integer.parseInt(partial.get("frequencia")));
            user.setPesoAtual(Double.parseDouble(partial.get("peso")));

            if (partial.containsKey("altura")) {
                user.setAlturaCm(Integer.parseInt(partial.get("altura")));
            }

            user.setOnboardingConcluido(true);
            userRepository.save(user);

            log.info("Onboarding concluído para user={} (chatId={})", user.getId(), user.getTelegramChatId());

            return FlowResult.done(
                    String.format("""
                            Perfeito, %s! 🎉 Seu perfil está salvo e estamos prontos para começar!
                            
                            A partir de agora eu sou seu parceiro de treino e evolução. Pode contar comigo para registrar treinos, acompanhar seu progresso e te manter motivado! 💪
                            
                            Para ver tudo que posso fazer, manda /menu
                            Para entender melhor como funciono, manda /ajuda
                            
                            Bora começar essa jornada juntos! 🚀""", safe(partial.get("nome"))),
                    "Use /menu para ver as opções ou /treino para pedir seu primeiro treino!"
            );
        }

        if (lower.startsWith("nao") || lower.startsWith("não") || lower.equals("n")
                || lower.equals("refazer") || lower.equals("corrigir")) {
            return FlowResult.text(
                    "Sem problema! \uD83D\uDE09 Vamos refazer desde o início. Como você gosta de ser chamado?",
                    ConversationFlowType.ONBOARDING,
                    STEP_NOME,
                    Map.of(),
                    null
            );
        }

        return FlowResult.text(
                "Me manda \"sim\" para confirmar ou \"não\" para refazer o cadastro \uD83D\uDE09",
                ConversationFlowType.ONBOARDING, STEP_CONFIRMACAO, partial, null
        );
    }

    // ========================================================================
    // UTILITÁRIOS
    // ========================================================================

    private String buildResumo(Map<String, String> partial) {
        String nome = safe(partial.get("nome"));

        String objetivo = partial.containsKey("objetivo")
                ? GoalType.valueOf(partial.get("objetivo")).getLabel() : "-";
        if (partial.containsKey("objetivosSecundarios")) {
            objetivo += " + " + partial.get("objetivosSecundarios");
        }

        String nivel = partial.containsKey("nivel")
                ? LevelType.valueOf(partial.get("nivel")).getLabel() : "-";

        String freq = partial.containsKey("frequencia")
                ? partial.get("frequencia") + "x/semana" : "-";

        String peso = partial.containsKey("peso")
                ? String.format("%.1f kg", Double.parseDouble(partial.get("peso"))) : "-";

        String altura = partial.containsKey("altura")
                ? partial.get("altura") + " cm" : "não informada";

        return String.format("""
                \uD83D\uDCCB Deixa eu ver se entendi tudo certinho:
                
                \uD83D\uDC64 Nome: %s
                \uD83C\uDFAF Objetivo: %s
                \uD83D\uDCAA Nível: %s
                \uD83D\uDCC5 Frequência semanal: %s
                \u2696\uFE0F Peso atual: %s
                \uD83D\uDCCF Altura: %s""", nome, objetivo, nivel, freq, peso, altura);
    }

    /**
     * Parse múltiplos objetivos de uma entrada como "1 e 3", "2,5", "1,2,6".
     */
    private List<GoalType> parseMultipleGoals(String input) {
        if (input == null || input.isBlank()) return List.of();

        String normalized = replacePortugueseNumberWords(normalizeForParsing(input));

        LinkedHashSet<GoalType> detectedGoals = new LinkedHashSet<>();
        Matcher digitMatcher = Pattern.compile("\\b([1-6])\\b").matcher(normalized);
        while (digitMatcher.find()) {
            GoalType goal = GoalType.fromUserInput(digitMatcher.group(1));
            if (goal != null) {
                detectedGoals.add(goal);
            }
        }
        if (!detectedGoals.isEmpty()) {
            return List.copyOf(detectedGoals);
        }

        // Se é uma entrada simples (sem separadores), tenta parse direto
        if (!normalized.contains(",") && !normalized.contains(" e ") && !normalized.contains("/")) {
            GoalType single = GoalType.fromUserInput(input);
            if (single != null) {
                return List.of(single);
            }

            single = detectGoalByKeywords(normalized);
            return single != null ? List.of(single) : List.of();
        }

        // Multi: separa por vírgula, "e", "/" sem regex custosa
        String normalizedSeparators = normalized.replace('/', ',').replace(" e ", ",").replace(" ou ", ",");
        String[] parts = normalizedSeparators.split(",");
        List<GoalType> goals = new java.util.ArrayList<>();
        for (String part : parts) {
            String cleaned = cleanOptionToken(part);
            GoalType g = GoalType.fromUserInput(cleaned);
            if (g == null) {
                g = detectGoalByKeywords(cleaned);
            }
            if (g != null && !goals.contains(g)) {
                goals.add(g);
            }
        }
        return goals;
    }

    private Map<String, String> loadPartialData(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Erro ao deserializar partialData, iniciando vazio: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Double parseWeight(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim()
                    .toLowerCase()
                    .replace("kg", "")
                    .replace(",", ".")
                    .trim();
            double value = Double.parseDouble(cleaned);
            return (value >= 20 && value <= 350) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse altura em cm, aceitando inteiro (173) ou decimal (173.5 ou 173,5).
     * Arredonda para inteiro.
     */
    private Integer parseHeightCm(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim()
                    .toLowerCase()
                    .replace("cm", "")
                    .replace(",", ".")
                    .trim();
            double value = Double.parseDouble(cleaned);
            int cm = (int) Math.round(value);
            return (cm >= 100 && cm <= 250) ? cm : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntegerInRange(String text, int min, int max) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim().replaceAll("[^0-9]", "");
            if (cleaned.isEmpty()) {
                cleaned = extractPortugueseNumber(text);
            }
            if (cleaned.isEmpty()) return null;
            int value = Integer.parseInt(cleaned);
            return (value >= min && value <= max) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========================================================================
    // CORREÇÃO / VOLTAR
    // ========================================================================

    /**
     * Detecta se o usuário quer corrigir um dado anterior.
     * Aceita: "voltar", "corrigir", "errei", "quero corrigir X", "mudar X".
     * Retorna o step-label para o qual voltar, ou null se não é correção.
     */
    private String detectBackCorrection(String text, int currentStep, Map<String, String> partial) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase().trim();

        // Palavras que indicam intenção de corrigir
        boolean wantsBack = lower.contains("voltar") || lower.contains("corrigir")
                || lower.contains("errei") || lower.contains("mudar")
                || lower.contains("alterar") || lower.contains("trocar")
                || lower.startsWith("quero corrigir") || lower.startsWith("quero mudar")
                || lower.contains("na verdade") || lower.contains("não era isso")
                || lower.contains("nao era isso");

        if (!wantsBack) return null;

        // Tenta identificar qual campo quer corrigir
        if (lower.contains("nome")) return "nome";
        if (lower.contains("objetivo")) return "objetivo";
        if (lower.contains("nível") || lower.contains("nivel")) return "nivel";
        if (lower.contains("frequência") || lower.contains("frequencia")
                || lower.contains("vezes") || lower.contains("semana") || lower.contains("dia")) return "frequencia";
        if (lower.contains("peso")) return "peso";
        if (lower.contains("altura")) return "altura";

        // Se não especificou qual, volta um step
        if (currentStep > STEP_NOME) {
            return switch (currentStep) {
                case STEP_OBJETIVO -> "nome";
                case STEP_NIVEL -> "objetivo";
                case STEP_FREQ -> "nivel";
                case STEP_PESO -> "frequencia";
                case STEP_ALTURA -> "peso";
                case STEP_CONFIRMACAO -> "altura";
                default -> null;
            };
        }

        return null;
    }

    /**
     * Volta ao step solicitado, mantendo os dados parciais já coletados.
     */
    private FlowResult handleBackCorrection(String target, Map<String, String> partial) {
        return switch (target) {
            case "nome" -> FlowResult.text(
                    "Sem problema! \uD83D\uDE09 Como você gosta de ser chamado?",
                    ConversationFlowType.ONBOARDING, STEP_NOME, partial, null);
            case "objetivo" -> FlowResult.text(
                    """
                    Beleza! Vamos corrigir o objetivo \uD83C\uDFAF
                    
                    1 - Emagrecer \uD83D\uDD25
                    2 - Ganhar massa muscular \uD83D\uDCAA
                    3 - Melhorar condicionamento \uD83C\uDFC3
                    4 - Correr 5km / 10km \uD83C\uDFC5
                    5 - Saúde e bem-estar geral \uD83C\uDF3F
                    6 - Ganhar força \uD83E\uDDBE
                    
                    Pode escolher mais de um! Ex: "1 e 3\"""",
                    ConversationFlowType.ONBOARDING, STEP_OBJETIVO, partial, null);
            case "nivel" -> FlowResult.text(
                    """
                    Bora corrigir o nível! \uD83D\uDCAA
                    
                    1 - Iniciante \uD83C\uDF31
                    2 - Intermediário \uD83D\uDCAA
                    3 - Avançado \uD83D\uDD25""",
                    ConversationFlowType.ONBOARDING, STEP_NIVEL, partial, null);
            case "frequencia" -> FlowResult.text(
                    "Certo! Quantos dias por semana você pretende treinar? (1 a 7) \uD83D\uDCC5",
                    ConversationFlowType.ONBOARDING, STEP_FREQ, partial, null);
            case "peso" -> FlowResult.text(
                    "Ok! Qual o seu peso atual em kg? (Ex: 72 ou 72.5) \u2696\uFE0F",
                    ConversationFlowType.ONBOARDING, STEP_PESO, partial, null);
            case "altura" -> FlowResult.text(
                    "Certo! Qual a sua altura em cm? (Ex: 175 ou 175,5) \uD83D\uDCCF\nManda \"pular\" se preferir.",
                    ConversationFlowType.ONBOARDING, STEP_ALTURA, partial, null);
            default -> null;
        };
    }

    private String extractPreferredName(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String cleaned = input.trim()
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ");

        Pattern phrasePattern = Pattern.compile(
                "^(?:pode\\s+me\\s+chamar(?:\\s+de)?|me\\s+chama\\s+de|me\\s+chame\\s+de|meu\\s+nome\\s+(?:e|é)|eu\\s+sou|sou|me\\s+chamo|chamo(?:-me)?)\\s+(.+)$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher matcher = phrasePattern.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).trim();
        }

        cleaned = cleaned
                .replaceAll("(?i)^(?:de\\s+)+", "")
                .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                .replaceAll("[.!?;:,]+$", "")
                .replaceAll("(?i)\\b(?:por favor|pfv|obrigado|obrigada|valeu)\\b.*$", "")
                .trim();

        if (cleaned.isBlank() || cleaned.length() > 40 || cleaned.split("\\s+").length > 4) {
            return null;
        }

        return normalizeNameCasing(cleaned);
    }

    private String normalizeNameCasing(String name) {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        return Pattern.compile("\\s+")
                .splitAsStream(name.trim())
                .filter(part -> !part.isBlank())
                .map(part -> {
                    if (part.length() <= 2 && part.equals(part.toUpperCase(ptBr))) {
                        return part;
                    }
                    String lower = part.toLowerCase(ptBr);
                    return lower.substring(0, 1).toUpperCase(ptBr) + lower.substring(1);
                })
                .collect(Collectors.joining(" "));
    }

    private String normalizeForParsing(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[.;:!?()\\[\\]\"]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String replacePortugueseNumberWords(String input) {
        String normalized = " " + input + " ";
        Map<String, String> numbers = Map.ofEntries(
                Map.entry(" um ", " 1 "),
                Map.entry(" uma ", " 1 "),
                Map.entry(" primeiro ", " 1 "),
                Map.entry(" primeira ", " 1 "),
                Map.entry(" dois ", " 2 "),
                Map.entry(" duas ", " 2 "),
                Map.entry(" segundo ", " 2 "),
                Map.entry(" segunda ", " 2 "),
                Map.entry(" tres ", " 3 "),
                Map.entry(" terceiro ", " 3 "),
                Map.entry(" terceira ", " 3 "),
                Map.entry(" quatro ", " 4 "),
                Map.entry(" quarto ", " 4 "),
                Map.entry(" quatro dias ", " 4 "),
                Map.entry(" cinco ", " 5 "),
                Map.entry(" quinto ", " 5 "),
                Map.entry(" quinta ", " 5 "),
                Map.entry(" seis ", " 6 "),
                Map.entry(" sexto ", " 6 "),
                Map.entry(" sete ", " 7 "),
                Map.entry(" setimo ", " 7 "),
                Map.entry(" oitavo ", " 8 "),
                Map.entry(" oito ", " 8 "),
                Map.entry(" nove ", " 9 "),
                Map.entry(" dez ", " 10 ")
        );

        for (Map.Entry<String, String> entry : numbers.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }

        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String extractPortugueseNumber(String input) {
        String normalized = replacePortugueseNumberWords(normalizeForParsing(input));
        Matcher matcher = Pattern.compile("\\b\\d{1,2}\\b").matcher(normalized);
        return matcher.find() ? matcher.group() : "";
    }

    private String cleanOptionToken(String token) {
        return token == null ? "" : token
                .replaceAll("[^a-zA-Z0-9À-ÿ\\s-]", " ")
                .replaceAll("\\b(?:opcao|opcoes|numero|numeros|objetivo|objetivos)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private GoalType detectGoalByKeywords(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.contains("emagrec") || normalized.contains("perder peso") || normalized.contains("secar")) {
            return GoalType.EMAGRECER;
        }
        if (normalized.contains("massa") || normalized.contains("musculo") || normalized.contains("hipertrof")) {
            return GoalType.GANHAR_MUSCULO;
        }
        if (normalized.contains("condicionamento") || normalized.contains("resistencia") || normalized.contains("folego")) {
            return GoalType.MELHORAR_CONDICIONAMENTO;
        }
        if (normalized.contains("5k") || normalized.contains("10k") || normalized.contains("correr") || normalized.contains("corrida")) {
            return GoalType.CORRER_5K_10K;
        }
        if (normalized.contains("saude") || normalized.contains("bem estar") || normalized.contains("qualidade de vida")) {
            return GoalType.SAUDE_BEM_ESTAR;
        }
        if (normalized.contains("forca") || normalized.contains("powerlifting") || normalized.contains("strongman")) {
            return GoalType.GANHAR_FORCA;
        }
        return null;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
