package br.edu.puc.fitjourneyai.orchestrator;

import br.edu.puc.fitjourneyai.domain.entity.Message;
import br.edu.puc.fitjourneyai.domain.entity.User;
import br.edu.puc.fitjourneyai.domain.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.domain.enums.GoalType;
import br.edu.puc.fitjourneyai.domain.enums.LevelType;
import br.edu.puc.fitjourneyai.domain.enums.MessageType;
import br.edu.puc.fitjourneyai.domain.repository.MessageRepository;
import br.edu.puc.fitjourneyai.domain.repository.UserRepository;
import br.edu.puc.fitjourneyai.dto.internal.IncomingMessage;
import br.edu.puc.fitjourneyai.dto.internal.InternalMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageOrchestrator {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    // ---------- Constantes de passos do ONBOARDING ----------
    private static final int ONBOARDING_STEP_NOME = 1;
    private static final int ONBOARDING_STEP_OBJETIVO = 2;
    private static final int ONBOARDING_STEP_NIVEL = 3;
    private static final int ONBOARDING_STEP_FREQ = 4;
    private static final int ONBOARDING_STEP_PESO = 5;
    private static final int ONBOARDING_STEP_ALTURA = 6;
    private static final int ONBOARDING_STEP_CONFIRMACAO = 7;

    // ---------- Constantes de passos do fluxo de medidas ----------
    private static final int REG_MED_STEP_PESO = 1;
    private static final int REG_MED_STEP_CINTURA = 2;
    private static final int REG_MED_STEP_QUADRIL = 3;
    private static final int REG_MED_STEP_PEITO = 4;
    private static final int REG_MED_STEP_BRACO = 5;

    // ---------- Constantes de passos do fluxo de treino feito ----------
    private static final int REG_TREINO_STEP_GRUPO = 1;
    private static final int REG_TREINO_STEP_DURACAO = 2;
    private static final int REG_TREINO_STEP_INTENSIDADE = 3;
    private static final int REG_TREINO_STEP_OBS = 4;

    // Número simples (ex.: "72", "72.5", "72kg")
    private static final Pattern PLAIN_NUMBER_PATTERN =
            Pattern.compile("^\\s*\\d+(?:[\\.,]\\d+)?\\s*(kg)?\\s*$", Pattern.CASE_INSENSITIVE);

    // Contextos em memória para fluxos multi-step
    private final Map<Long, RegistroMedidasContext> medidasContextByChatId = new ConcurrentHashMap<>();
    private final Map<Long, RegistroTreinoContext> treinoContextByChatId = new ConcurrentHashMap<>();

    /**
     * Intenções reconhecidas pela camada de orquestração.
     */
    private enum IntentType {
        START,
        MENU,
        AJUDA,
        REGISTRO,
        REGISTRO_PESO,
        REGISTRO_MEDIDAS,
        TREINO,
        TREINO_FEITO,
        PROGRESSO,
        RESUMO,
        CONFIG,
        UNKNOWN
    }

    /**
     * Ponto de entrada principal. Recebe a mensagem "crua" vinda do Webhook,
     * garante que o usuário exista, identifica intenção, delega para handlers
     * e devolve o texto de resposta que será enviado ao TelegramService.
     */
    public String handleIncomingMessage(InternalMessage internalMessage) {
        if (internalMessage == null || internalMessage.getChatId() == null) {
            log.warn("InternalMessage inválida: {}", internalMessage);
            return null;
        }

        // carrega ou cria usuário
        User user = userRepository.findByTelegramChatId(internalMessage.getChatId())
                .orElseGet(() -> createNewUser(internalMessage.getChatId()));

        // atualiza última interação
        user.setLastInteractionAt(internalMessage.getDataHora());
        userRepository.save(user);

        IncomingMessage incoming = IncomingMessage.builder()
                .user(user)
                .chatId(internalMessage.getChatId())
                .texto(internalMessage.getTexto())
                .dataHora(internalMessage.getDataHora())
                .build();

        // registra mensagem do usuário
        persistUserMessage(incoming);

        // identifica intenção
        IntentType intent = detectIntent(incoming);

        // decide resposta com base na intenção e no estado de fluxo
        String responseText = routeToHandler(intent, incoming);

        // registra mensagem do bot (se houver resposta)
        if (responseText != null && !responseText.isBlank()) {
            persistBotMessage(user, responseText);
        }

        return responseText;
    }

    private User createNewUser(Long chatId) {
        log.info("Criando novo usuário para chatId={}", chatId);
        User user = User.builder()
                .telegramChatId(chatId)
                .onboardingConcluido(false)
                .nudgesEnabled(true)
                .currentFlow(ConversationFlowType.NONE)
                .currentStep(null)
                .build();

        return userRepository.save(user);
    }

    private void persistUserMessage(IncomingMessage incoming) {
        Message msg = Message.builder()
                .user(incoming.getUser())
                .conteudo(incoming.getTexto())
                .tipo(MessageType.USER)
                .dataHora(incoming.getDataHora())
                .build();

        messageRepository.save(msg);
    }

    private void persistBotMessage(User user, String texto) {
        Message msg = Message.builder()
                .user(user)
                .conteudo(texto)
                .tipo(MessageType.BOT)
                .dataHora(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        messageRepository.save(msg);
    }

    // ========================================================================
    // DETECÇÃO DE INTENÇÃO
    // ========================================================================

    /**
     * Identifica intenção principal de forma simples, baseada em comandos/textos.
     * Depois podemos complementar com IA (classifyIntentIfNeeded).
     */
    private IntentType detectIntent(IncomingMessage incoming) {
        String text = incoming.getTexto();
        if (text == null) {
            return IntentType.UNKNOWN;
        }

        String trimmed = text.trim();

        // comandos exatos
        switch (trimmed.toLowerCase()) {
            case "/start":
                return IntentType.START;
            case "/menu":
                return IntentType.MENU;
            case "/ajuda":
                return IntentType.AJUDA;
            case "/registro":
                return IntentType.REGISTRO;
            case "/peso":
                return IntentType.REGISTRO_PESO;
            case "/medidas":
                return IntentType.REGISTRO_MEDIDAS;
            case "/treino":
                return IntentType.TREINO;
            case "/treino_feito":
                return IntentType.TREINO_FEITO;
            case "/progresso":
                return IntentType.PROGRESSO;
            case "/resumo":
                return IntentType.RESUMO;
            case "/config":
                return IntentType.CONFIG;
            default:
                // palavras-chave simples
                String lower = trimmed.toLowerCase();
                if (lower.contains("treino")) {
                    return IntentType.TREINO;
                }
                if (lower.contains("peso")) {
                    return IntentType.REGISTRO_PESO;
                }
                if (lower.contains("medida")) {
                    return IntentType.REGISTRO_MEDIDAS;
                }
                if (lower.contains("progresso")) {
                    return IntentType.PROGRESSO;
                }
                if (lower.contains("resumo")) {
                    return IntentType.RESUMO;
                }
                if (lower.contains("ajuda") || lower.contains("menu")) {
                    return IntentType.AJUDA;
                }

                // Se o usuário mandar apenas um número (ex.: "72", "72.5kg"), tratamos como peso
                if (isPlainNumber(trimmed)) {
                    return IntentType.REGISTRO_PESO;
                }

                return IntentType.UNKNOWN;
        }
    }

    private boolean isPlainNumber(String text) {
        if (text == null) {
            return false;
        }
        return PLAIN_NUMBER_PATTERN.matcher(text).matches();
    }

    // ========================================================================
    // ROTEAMENTO DE INTENÇÃO / FLUXO ATIVO
    // ========================================================================

    /**
     * Roteia para o handler específico de acordo com a intenção.
     */
    private String routeToHandler(IntentType intent, IncomingMessage incoming) {
        User user = incoming.getUser();

        // Se usuário estiver em algum fluxo ativo, prioriza esse fluxo
        if (user.getCurrentFlow() != null && user.getCurrentFlow() != ConversationFlowType.NONE) {
            return handleActiveFlow(incoming);
        }

        switch (intent) {
            case START:
                return handleStart(incoming);
            case MENU:
                return handleMenu(incoming);
            case AJUDA:
                return handleAjuda(incoming);
            case REGISTRO:
                return handleRegistro(incoming);
            case REGISTRO_PESO:
                return handleRegistroPeso(incoming);
            case REGISTRO_MEDIDAS:
                return handleRegistroMedidas(incoming);
            case TREINO:
                return handleTreino(incoming);
            case TREINO_FEITO:
                return handleTreinoFeito(incoming);
            case PROGRESSO:
                return handleProgresso(incoming);
            case RESUMO:
                return handleResumo(incoming);
            case CONFIG:
                return handleConfig(incoming);
            case UNKNOWN:
            default:
                // 6.9 – tentativa opcional de classificação por IA
                IntentType classified = maybeClassifyIntentWithAi(incoming);
                if (classified != IntentType.UNKNOWN) {
                    return routeToHandler(classified, incoming);
                }
                return handleUnknown(incoming);
        }
    }

    /**
     * Handler genérico para quando o usuário já está em um fluxo (onboarding, registro guiado, etc.).
     */
    private String handleActiveFlow(IncomingMessage incoming) {
        User user = incoming.getUser();
        ConversationFlowType flow = user.getCurrentFlow();

        if (flow == null || flow == ConversationFlowType.NONE) {
            // fallback de segurança
            user.setCurrentStep(null);
            userRepository.save(user);
            return handleUnknown(incoming);
        }

        switch (flow) {
            case ONBOARDING:
                return handleOnboardingFlow(incoming);
            case REGISTRO_PESO:
                return handleRegistroPesoFlow(incoming);
            case REGISTRO_MEDIDAS:
                return handleRegistroMedidasFlow(incoming);
            case REGISTRO_TREINO:
                return handleRegistroTreinoFlow(incoming);
            default:
                // Outros fluxos futuros
                user.setCurrentFlow(ConversationFlowType.NONE);
                user.setCurrentStep(null);
                userRepository.save(user);
                return """
                        Me perdi um pouco no fluxo em que estávamos 😅

                        Vou voltar para o menu principal. Use /menu para ver as opções.
                        """;
        }
    }

    // ========================================================================
    // 6.1 – FLUXO DE ONBOARDING (/start)
    // ========================================================================

    private String handleStart(IncomingMessage incoming) {
        User user = incoming.getUser();

        // Se já concluiu onboarding, mostra menu direto
        if (user.isOnboardingConcluido()) {
            return handleMenu(incoming);
        }

        // Prepara para fluxo de onboarding (nome, objetivo, nível, frequência, peso, altura, confirmação)
        user.setCurrentFlow(ConversationFlowType.ONBOARDING);
        user.setCurrentStep(ONBOARDING_STEP_NOME);
        userRepository.save(user);

        return """
                👋 Oi! Eu sou o FitJourneyAI, seu parceiro de treinos e progresso fitness.

                Vamos começar configurando seu perfil rapidinho.
                Primeiro, me conta: qual é o seu nome?
                """;
    }

    private String handleOnboardingFlow(IncomingMessage incoming) {
        User user = incoming.getUser();
        String texto = safe(incoming.getTexto());
        String lower = texto.toLowerCase().trim();

        Integer step = user.getCurrentStep();
        if (step == null || step < ONBOARDING_STEP_NOME || step > ONBOARDING_STEP_CONFIRMACAO) {
            user.setCurrentStep(ONBOARDING_STEP_NOME);
            userRepository.save(user);
            return """
                Vamos recomeçar o cadastro rapidinho 😊

                Qual é o seu nome?
                """;
        }

        switch (step) {

            // 1) NOME --------------------------------------------------------------
            case ONBOARDING_STEP_NOME:
                if (texto.isBlank()) {
                    return "Não entendi seu nome 😅\n\nMe manda como você prefere ser chamado.";
                }

                user.setNome(texto.trim());
                user.setCurrentStep(ONBOARDING_STEP_OBJETIVO);
                userRepository.save(user);

                return """
                        Prazer, %s! 😄
                        
                        Agora me conta: qual é o SEU objetivo principal hoje?
                        
                        Exemplos:
                        - Emagrecer
                        - Ganhar massa muscular
                        - Melhorar condicionamento
                        - Correr 5km / 10km
                        """.formatted(user.getNome());

            // 2) OBJETIVO ----------------------------------------------------------
            case ONBOARDING_STEP_OBJETIVO: {
                if (texto.isBlank()) {
                    return """
                            Não entendi seu objetivo 🤔
                            
                            Me diga algo como:
                            - Emagrecer
                            - Ganhar massa muscular
                            - Melhorar condicionamento
                            - Correr 5km / 10km
                            """;
                }

                GoalType goal = GoalType.fromUserInput(texto);
                if (goal == null) {
                    return """
                            Ainda não consegui identificar bem seu objetivo 🤔
                            
                            Tente responder com algo como:
                            - Emagrecer
                            - Ganhar massa muscular
                            - Melhorar condicionamento
                            - Correr 5km / 10km
                            """;
                }

                user.setObjetivo(goal);
                user.setCurrentStep(ONBOARDING_STEP_NIVEL);
                userRepository.save(user);

                return """
                        Objetivo anotado ✅
                        
                        Agora me diz: qual é o seu nível atual?
                        - iniciante
                        - intermediário
                        - avançado
                        """;
            }

            // 3) NÍVEL -------------------------------------------------------------
            case ONBOARDING_STEP_NIVEL: {
                LevelType level = parseLevelFromText(texto);

                if (level == null) {
                    return """
                            Não reconheci seu nível 🤔
                            
                            Responda com:
                            - iniciante
                            - intermediário
                            - avançado
                            """;
                }

                user.setNivel(level);
                user.setCurrentStep(ONBOARDING_STEP_FREQ);
                userRepository.save(user);

                return """
                        Perfeito 🙌
                        
                        Quantos dias por semana você consegue treinar?
                        (Responda com um número de 1 a 7. Ex.: 3)
                        """;
            }

            // 4) FREQUÊNCIA --------------------------------------------------------
            case ONBOARDING_STEP_FREQ: {
                Integer freq = parseIntegerInRange(texto, 1, 7);
                if (freq == null) {
                    return """
                            Para eu te ajudar bem, me manda um número entre 1 e 7 😊
                            
                            Quantos dias por semana você consegue treinar?
                            """;
                }

                user.setFrequenciaTreinoEstimada(freq);
                user.setCurrentStep(ONBOARDING_STEP_PESO);
                userRepository.save(user);

                return """
                        Boa! 🏋️‍♂️
                        
                        Agora, qual é o seu peso atual em kg?
                        (Ex.: 72.5)
                        """;
            }

            // 5) PESO --------------------------------------------------------------
            case ONBOARDING_STEP_PESO: {
                Double peso = parsePeso(texto);
                if (peso == null) {
                    return """
                            Não consegui entender esse peso 😅
                            
                            Me manda algo como:
                            - 72
                            - 72.5
                            """;
                }

                user.setPesoAtual(peso);
                user.setCurrentStep(ONBOARDING_STEP_ALTURA);
                userRepository.save(user);

                return """
                        Peso anotado ✅
                        
                        Agora me diz sua altura em centímetros.
                        Ex.: 175
                        """;
            }

            // 6) ALTURA (opcional) -------------------------------------------------
            case ONBOARDING_STEP_ALTURA: {
                if (lower.isBlank()
                        || lower.contains("pular")
                        || lower.contains("nao quero")
                        || lower.contains("não quero")) {

                    user.setCurrentStep(ONBOARDING_STEP_CONFIRMACAO);
                    userRepository.save(user);
                    return buildOnboardingResumo(user);
                }

                Integer altura = parseAlturaCm(texto);
                if (altura == null) {
                    return """
                            Esse valor de altura está estranho 🤔
                            
                            Me manda sua altura em centímetros, algo entre 120 e 230.
                            Exemplos:
                            - 175
                            - 173,5
                            - 1,73m
                            """;
                }

                user.setAlturaCm(altura);
                user.setCurrentStep(ONBOARDING_STEP_CONFIRMACAO);
                userRepository.save(user);
                return buildOnboardingResumo(user);
            }

            // 7) CONFIRMAÇÃO -------------------------------------------------------
            case ONBOARDING_STEP_CONFIRMACAO: {
                if (lower.startsWith("s")) { // sim, s, simmm, "sim pode"
                    user.setOnboardingConcluido(true);
                    user.setCurrentFlow(ConversationFlowType.NONE);
                    user.setCurrentStep(null);
                    userRepository.save(user);

                    return """
                        Top! Onboarding concluído ✅

                        Com essas informações eu já consigo montar treinos bem mais alinhados com a sua realidade.

                        A partir de agora você pode:
                        - Ver opções: /menu
                        - Pedir um treino: "Quero um treino de pernas"
                        - Focar em corrida: "Quero focar em corrida de 5km"

                        Quando quiser, manda:
                        "Quero um treino para hoje"
                        que eu te ajudo a começar 💪
                        """;
                }

                if (lower.startsWith("n")) { // não, n, "não gostei", etc.
                    user.setCurrentStep(ONBOARDING_STEP_NOME);
                    userRepository.save(user);

                    return """
                        Sem problema, vamos ajustar então 😄

                        Vamos recomeçar. Qual é o seu nome?
                        """;
                }

                return """
                    Só pra confirmar: posso salvar seus dados assim?

                    Responda:
                    - "sim" para confirmar
                    - "não" para refazer
                    """;
            }

            // fallback de segurança ------------------------------------------------
            default:
                user.setCurrentStep(ONBOARDING_STEP_NOME);
                userRepository.save(user);
                return """
                    Vamos recomeçar o cadastro 😊

                    Qual é o seu nome?
                    """;
        }
    }


    private String buildOnboardingResumo(User user) {
        String nome = safe(user.getNome());

        String objetivoStr = user.getObjetivo() != null
                ? user.getObjetivo().getLabel()
                : "-";

        String nivelStr = user.getNivel() != null
                ? user.getNivel().getLabel()
                : "-";

        String freqStr = user.getFrequenciaTreinoEstimada() != null
                ? user.getFrequenciaTreinoEstimada() + "x/semana"
                : "-";

        String pesoStr = user.getPesoAtual() != null
                ? String.format("%.1f kg", user.getPesoAtual()).replace('.', ',')
                : "-";

        String alturaStr = user.getAlturaCm() != null
                ? user.getAlturaCm() + " cm"
                : "-";

        return """
            Perfeito! Deixa eu ver se entendi tudo certinho 👇

            Nome: %s
            Objetivo: %s
            Nível: %s
            Frequência semanal: %s
            Peso atual: %s
            Altura: %s

            Posso salvar esses dados? (responda "sim" ou "não")
            """.formatted(
                nome,
                objetivoStr,
                nivelStr,
                freqStr,
                pesoStr,
                alturaStr
        );
    }


    // ========================================================================
    // 6.3 – FLUXO REGISTRO DE PESO (/peso ou número isolado)
    // ========================================================================

    private String handleRegistroPeso(IncomingMessage incoming) {
        User user = incoming.getUser();
        user.setCurrentFlow(ConversationFlowType.REGISTRO_PESO);
        user.setCurrentStep(REG_MED_STEP_PESO); // reutilizando step 1 como "esperando peso"
        userRepository.save(user);

        return """
                Vamos registrar seu peso atual. ⚖️

                Me envie seu peso em kg.
                Exemplo: 80.2
                """;
    }

    private String handleRegistroPesoFlow(IncomingMessage incoming) {
        User user = incoming.getUser();
        String texto = safe(incoming.getTexto());

        Double novoPeso = parsePeso(texto);
        if (novoPeso == null) {
            return """
                    Não consegui entender esse peso 😅

                    Me manda algo como:
                    - 72
                    - 72.5
                    - 72kg
                    """;
        }

        Double pesoAnterior = user.getPesoAtual();
        user.setPesoAtual(novoPeso);
        user.setCurrentFlow(ConversationFlowType.NONE);
        user.setCurrentStep(null);
        userRepository.save(user);

        String diffMsg;
        if (pesoAnterior == null) {
            diffMsg = "Esse é o seu primeiro registro de peso por aqui. Bora acompanhar a evolução! 🚀";
        } else {
            double diff = novoPeso - pesoAnterior;
            if (Math.abs(diff) < 0.01) {
                diffMsg = "Seu peso se manteve estável em relação ao último registro.";
            } else if (diff > 0) {
                diffMsg = String.format("Você aumentou aproximadamente %.1f kg desde o último registro.", diff);
            } else {
                diffMsg = String.format("Você reduziu aproximadamente %.1f kg desde o último registro. Mandou bem! 🔥", Math.abs(diff));
            }
        }

        return """
                Peso registrado com sucesso: %.1f kg ✅

                %s
                """.formatted(novoPeso, diffMsg);
    }

    // ========================================================================
    // 6.4 – FLUXO REGISTRO DE MEDIDAS (/medidas ou /registro)
    // ========================================================================

    private String handleRegistro(IncomingMessage incoming) {
        User user = incoming.getUser();
        user.setCurrentFlow(ConversationFlowType.REGISTRO_MEDIDAS);
        user.setCurrentStep(REG_MED_STEP_PESO);
        userRepository.save(user);

        // Cria / reseta contexto em memória
        medidasContextByChatId.put(incoming.getChatId(), new RegistroMedidasContext());

        return """
                Vamos registrar seus dados! 📝

                Primeiro, me informe seu peso atual em kg.
                Exemplo: 72.5
                """;
    }

    private String handleRegistroMedidas(IncomingMessage incoming) {
        User user = incoming.getUser();
        user.setCurrentFlow(ConversationFlowType.REGISTRO_MEDIDAS);
        user.setCurrentStep(REG_MED_STEP_PESO);
        userRepository.save(user);

        medidasContextByChatId.put(incoming.getChatId(), new RegistroMedidasContext());

        return """
                Beleza! Vamos registrar suas medidas corporais. 📏

                Primeiro, me envie seu peso em kg.
                Exemplo: 72.5
                """;
    }

    private String handleRegistroMedidasFlow(IncomingMessage incoming) {
        User user = incoming.getUser();
        Long chatId = incoming.getChatId();
        RegistroMedidasContext ctx = medidasContextByChatId.computeIfAbsent(chatId, id -> new RegistroMedidasContext());

        String texto = safe(incoming.getTexto());
        Integer step = user.getCurrentStep();
        if (step == null || step < REG_MED_STEP_PESO || step > REG_MED_STEP_BRACO) {
            user.setCurrentStep(REG_MED_STEP_PESO);
            userRepository.save(user);
            return """
                    Vamos começar o registro guiado de medidas 😊

                    Me envie seu peso em kg.
                    Exemplo: 72.5
                    """;
        }

        switch (step) {
            case REG_MED_STEP_PESO -> {
                Double peso = parsePeso(texto);
                if (peso == null) {
                    return """
                            Não consegui entender esse peso 😅

                            Me manda algo como:
                            - 72
                            - 72.5
                            """;
                }
                ctx.peso = peso;
                user.setPesoAtual(peso);
                user.setCurrentStep(REG_MED_STEP_CINTURA);
                userRepository.save(user);

                return """
                        Peso registrado ✅

                        Agora, me envie a medida da CINTURA em centímetros.
                        Exemplo: 82
                        """;
            }
            case REG_MED_STEP_CINTURA -> {
                Double cintura = parseMedida(texto);
                if (cintura == null) {
                    return """
                            Não consegui entender essa medida de cintura 🤔

                            Me manda um valor em centímetros, por exemplo:
                            - 82
                            - 90
                            """;
                }
                ctx.cintura = cintura;
                user.setCurrentStep(REG_MED_STEP_QUADRIL);
                userRepository.save(user);

                return """
                        Cintura registrada ✅

                        Agora, me envie a medida do QUADRIL em centímetros.
                        Exemplo: 98
                        """;
            }
            case REG_MED_STEP_QUADRIL -> {
                Double quadril = parseMedida(texto);
                if (quadril == null) {
                    return """
                            Não consegui entender essa medida de quadril 🤔

                            Me manda um valor em centímetros, por exemplo:
                            - 95
                            - 105
                            """;
                }
                ctx.quadril = quadril;
                user.setCurrentStep(REG_MED_STEP_PEITO);
                userRepository.save(user);

                return """
                        Quadril registrado ✅

                        Agora, me envie a medida do PEITO em centímetros.
                        Exemplo: 100
                        """;
            }
            case REG_MED_STEP_PEITO -> {
                Double peito = parseMedida(texto);
                if (peito == null) {
                    return """
                            Não consegui entender essa medida de peito 🤔

                            Me manda um valor em centímetros, por exemplo:
                            - 95
                            - 110
                            """;
                }
                ctx.peito = peito;
                user.setCurrentStep(REG_MED_STEP_BRACO);
                userRepository.save(user);

                return """
                        Peito registrado ✅

                        Agora, me envie a medida do BRAÇO (em repouso) em centímetros.
                        Exemplo: 35
                        """;
            }
            case REG_MED_STEP_BRACO -> {
                Double braco = parseMedida(texto);
                if (braco == null) {
                    return """
                            Não consegui entender essa medida de braço 🤔

                            Me manda um valor em centímetros, por exemplo:
                            - 32
                            - 38
                            """;
                }
                ctx.braco = braco;

                // Finaliza fluxo
                user.setCurrentFlow(ConversationFlowType.NONE);
                user.setCurrentStep(null);
                userRepository.save(user);
                medidasContextByChatId.remove(chatId);

                return buildResumoMedidas(ctx);
            }
            default -> {
                user.setCurrentFlow(ConversationFlowType.NONE);
                user.setCurrentStep(null);
                userRepository.save(user);
                medidasContextByChatId.remove(chatId);

                return """
                        Algo saiu do trilho no fluxo de medidas 😅

                        Vamos recomeçar depois com o comando /medidas.
                        """;
            }
        }
    }

    private String buildResumoMedidas(RegistroMedidasContext ctx) {
        return """
                Medidas registradas com sucesso ✅

                Peso: %s
                Cintura: %s
                Quadril: %s
                Peito: %s
                Braço: %s

                Em versões futuras vou usar essas medidas para acompanhar sua evolução e gerar gráficos 📊
                """.formatted(
                ctx.peso == null ? "-" : String.format("%.1f kg", ctx.peso),
                ctx.cintura == null ? "-" : String.format("%.1f cm", ctx.cintura),
                ctx.quadril == null ? "-" : String.format("%.1f cm", ctx.quadril),
                ctx.peito == null ? "-" : String.format("%.1f cm", ctx.peito),
                ctx.braco == null ? "-" : String.format("%.1f cm", ctx.braco)
        );
    }

    // ========================================================================
    // 6.5 – FLUXO REGISTRO DE TREINO REALIZADO (/treino_feito)
    // ========================================================================

    private String handleTreinoFeito(IncomingMessage incoming) {
        User user = incoming.getUser();
        user.setCurrentFlow(ConversationFlowType.REGISTRO_TREINO);
        user.setCurrentStep(REG_TREINO_STEP_GRUPO);
        userRepository.save(user);

        treinoContextByChatId.put(incoming.getChatId(), new RegistroTreinoContext());

        return """
                Show! Vamos registrar um treino que você realizou. ✅

                Me conte rapidamente qual tipo de treino você fez hoje
                (ex.: peito e tríceps, costas, pernas, corrida, etc.).
                """;
    }

    private String handleRegistroTreinoFlow(IncomingMessage incoming) {
        User user = incoming.getUser();
        Long chatId = incoming.getChatId();
        RegistroTreinoContext ctx = treinoContextByChatId.computeIfAbsent(chatId, id -> new RegistroTreinoContext());

        String texto = safe(incoming.getTexto());
        String lower = texto.toLowerCase().trim();

        Integer step = user.getCurrentStep();
        if (step == null || step < REG_TREINO_STEP_GRUPO || step > REG_TREINO_STEP_OBS) {
            user.setCurrentStep(REG_TREINO_STEP_GRUPO);
            userRepository.save(user);
            return """
                    Vamos recomeçar o registro do treino 😄

                    Me conte qual foi o tipo de treino que você fez hoje.
                    """;
        }

        switch (step) {
            case REG_TREINO_STEP_GRUPO -> {
                if (texto.isBlank()) {
                    return """
                            Não entendi qual treino você fez 🤔

                            Exemplos:
                            - peito e tríceps
                            - costas
                            - pernas
                            - corrida
                            """;
                }
                ctx.grupoMuscular = texto.trim();
                user.setCurrentStep(REG_TREINO_STEP_DURACAO);
                userRepository.save(user);

                return """
                        Boa! 💪

                        Aproximadamente quantos minutos durou esse treino?
                        (Ex.: 45)
                        """;
            }
            case REG_TREINO_STEP_DURACAO -> {
                Integer duracao = parseIntegerInRange(texto, 5, 300);
                if (duracao == null) {
                    return """
                            Não consegui entender essa duração 🤔

                            Me manda um número em minutos, algo entre 5 e 300.
                            Ex.: 45
                            """;
                }
                ctx.duracaoMinutos = duracao;
                user.setCurrentStep(REG_TREINO_STEP_INTENSIDADE);
                userRepository.save(user);

                return """
                        Entendido! ⏱️

                        Em uma escala de 1 a 10, qual foi a intensidade percebida desse treino?
                        (1 = muito leve, 10 = extremamente intensa)
                        """;
            }
            case REG_TREINO_STEP_INTENSIDADE -> {
                Integer intensidade = parseIntegerInRange(texto, 1, 10);
                if (intensidade == null) {
                    return """
                            Não consegui entender essa intensidade 🤔

                            Me manda um número de 1 a 10.
                            """;
                }
                ctx.intensidade = intensidade;
                user.setCurrentStep(REG_TREINO_STEP_OBS);
                userRepository.save(user);

                return """
                        Show! 🔥

                        Se quiser, me conta rapidamente quais foram os principais exercícios.
                        Se preferir pular, é só escrever "pular".
                        """;
            }
            case REG_TREINO_STEP_OBS -> {
                if (!lower.isBlank() && !lower.contains("pular")) {
                    ctx.observacoes = texto.trim();
                }

                // Aqui é o ponto ideal pra salvar em Workout (Fase 6.5)
                // Ex.: Workout workout = Workout.builder() ... build();

                user.setCurrentFlow(ConversationFlowType.NONE);
                user.setCurrentStep(null);
                userRepository.save(user);
                treinoContextByChatId.remove(chatId);

                return buildResumoTreino(ctx);
            }
            default -> {
                user.setCurrentFlow(ConversationFlowType.NONE);
                user.setCurrentStep(null);
                userRepository.save(user);
                treinoContextByChatId.remove(chatId);

                return """
                        Algo saiu do trilho no registro do treino 😅

                        Vamos recomeçar depois com o comando /treino_feito.
                        """;
            }
        }
    }

    private String buildResumoTreino(RegistroTreinoContext ctx) {
        return """
                Treino registrado com sucesso ✅

                Tipo/Grupo muscular: %s
                Duração aproximada: %s
                Intensidade percebida: %s
                Observações: %s

                Excelente trabalho! 💪
                Em versões futuras vou usar esses dados para montar resumos e gráficos do seu desempenho.
                """.formatted(
                ctx.grupoMuscular == null ? "-" : ctx.grupoMuscular,
                ctx.duracaoMinutos == null ? "-" : ctx.duracaoMinutos + " min",
                ctx.intensidade == null ? "-" : ctx.intensidade + "/10",
                ctx.observacoes == null || ctx.observacoes.isBlank() ? "-" : ctx.observacoes
        );
    }

    // ========================================================================
    // 6.6 – FLUXO DE GERAÇÃO DE TREINOS PERSONALIZADOS (/treino)
    // ========================================================================

    private String handleTreino(IncomingMessage incoming) {
        User user = incoming.getUser();

        if (!user.isOnboardingConcluido()) {
            return """
                    Antes de gerar um treino personalizado, preciso conhecer melhor você 😊

                    Use o comando /start para fazer o onboarding rapidinho.
                    """;
        }

        // Aqui, na Fase 6.6, é onde vamos:
        // - Montar um prompt estruturado com objetivo, nível, frequência, histórico
        // - Chamar OpenAiService.generateWorkoutPlan(...)
        // - Formatar a resposta em Markdown
        //
        // Por enquanto, deixamos um placeholder amigável:
        return """
                💪 Em breve vou conseguir gerar um treino personalizado usando IA
                com base no seu objetivo, nível e frequência de treinos.

                Enquanto isso, você pode me dizer em texto livre o que quer treinar hoje
                (ex.: "Quero um treino de pernas focado em força") e eu registro isso como referência.
                """;
    }

    // ========================================================================
    // 6.7 – PROGRESSO / 6.8 – RESUMO / 6.2 – MENU / AJUDA / 6.9 – FALLBACK
    // ========================================================================

    private String handleMenu(IncomingMessage incoming) {
        return """
                🧭 Menu FitJourneyAI

                Comandos principais:
                - /registro   → registrar peso e medidas (fluxo guiado)
                - /peso       → registrar apenas peso atual
                - /medidas    → registrar medidas corporais
                - /treino     → gerar (futuramente) um treino personalizado
                - /treino_feito → registrar um treino que você realizou
                - /progresso  → ver sua evolução (peso, treinos, etc.)
                - /resumo     → ver um resumo recente
                - /config     → ajustar preferências do bot

                Se estiver perdido, pode mandar /ajuda.
                """;
    }

    private String handleAjuda(IncomingMessage incoming) {
        return """
                🙋 Ajuda do FitJourneyAI

                Eu posso te ajudar a:
                - Registrar peso e medidas
                - Gerar treinos personalizados (futuramente com IA)
                - Registrar treinos realizados
                - Acompanhar seu progresso

                Use /menu para ver todos os comandos disponíveis.
                """;
    }

    private String handleProgresso(IncomingMessage incoming) {
        // Fase 6.7: aqui será feita a consulta de medidas e treinos,
        // cálculo de variações e, futuramente, geração de gráficos.
        return """
                📊 O painel de progresso detalhado ainda está em implementação.

                Na próxima etapa, vou conseguir te mostrar:
                - Variação de peso em um período
                - Variação de medidas (cintura, quadril, etc.)
                - Quantidade de treinos e média por semana

                Por enquanto, continue registrando peso, medidas e treinos
                para termos dados suficientes quando o painel estiver pronto 😉
                """;
    }

    private String handleResumo(IncomingMessage incoming) {
        // Fase 6.8: resumo textual simples dos últimos dias.
        return """
                🧾 O resumo automático ainda está em implementação.

                Em breve vou consolidar seus últimos registros de peso,
                medidas e treinos em um resumo simples pra você acompanhar.
                """;
    }

    private String handleConfig(IncomingMessage incoming) {
        return """
                ⚙️ Ajustes do FitJourneyAI em breve!

                Aqui você poderá configurar:
                - Preferências de notificação
                - Frequência semanal alvo
                - Se deseja receber mensagens de reengajamento
                """;
    }

    /**
     * 6.9 – Ponto de extensão para classificação de intenção via OpenAI.
     * Hoje só devolve UNKNOWN para não quebrar nada.
     *
     * Futuro (TCC2): injetar OpenAiService e mapear a intenção retornada.
     */
    private IntentType maybeClassifyIntentWithAi(IncomingMessage incoming) {
        // TODO: integrar OpenAiService aqui (classificação de intenção).
        return IntentType.UNKNOWN;
    }

    private String handleUnknown(IncomingMessage incoming) {
        return """
                🤔 Não consegui entender muito bem o que você quis dizer.

                Tenta usar um destes comandos:
                - /menu   → ver tudo o que posso fazer
                - /ajuda  → explicação rápida dos recursos

                Se quiser, você também pode só escrever:
                "Quero um treino de braços"
                "Quero registrar meu peso"
                """;
    }

    // ========================================================================
    // HELPERS DE PARSE / SANITIZAÇÃO
    // ========================================================================

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Double parsePeso(String texto) {
        if (texto == null) return null;
        String normalized = texto.toLowerCase()
                .replace("kg", "")
                .trim()
                .replace(",", ".");
        try {
            double value = Double.parseDouble(normalized);
            if (value < 30 || value > 300) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseMedida(String texto) {
        if (texto == null) return null;
        String normalized = texto.trim().replace(",", ".");
        try {
            double value = Double.parseDouble(normalized);
            if (value < 20 || value > 300) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntegerInRange(String texto, int min, int max) {
        if (texto == null) return null;
        String normalized = texto.trim();
        try {
            int value = Integer.parseInt(normalized);
            if (value < min || value > max) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LevelType parseLevelFromText(String rawText) {
        String t = normalizePt(rawText);

        if (t.contains("iniciante") || t.contains("comec")) {
            return LevelType.INICIANTE;
        }
        if (t.contains("intermedi")) {
            return LevelType.INTERMEDIARIO;
        }
        if (t.contains("avanc")) {
            return LevelType.AVANCADO;
        }

        return null;
    }

    private Integer parseAlturaCm(String raw) {
        if (raw == null) return null;

        String t = raw.trim().toLowerCase();

        // tira "m", espaços, etc (ex.: "1,73m")
        t = t.replace("m", "");

        // normaliza vírgula para ponto
        t = t.replace(",", ".");

        // mantém só dígitos e ponto
        t = t.replaceAll("[^0-9.]", "");

        if (t.isEmpty()) return null;

        try {
            double valor = Double.parseDouble(t);

            // se o cara digitar em metros (1.73), converte pra cm
            if (valor < 10) {
                valor = valor * 100; // 1.73 -> 173
            }

            int cm = (int) Math.round(valor);

            if (cm < 120 || cm > 230) {
                return null;
            }

            return cm;
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private String normalizePt(String text) {
        if (text == null) return "";
        String nfd = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "").toLowerCase().trim();
    }


    // ========================================================================
    // CONTEXTOS EM MEMÓRIA (REGISTRO MEDIDAS / TREINO)
    // ========================================================================

    private static class RegistroMedidasContext {
        Double peso;
        Double cintura;
        Double quadril;
        Double peito;
        Double braco;
    }

    private static class RegistroTreinoContext {
        String grupoMuscular;
        Integer duracaoMinutos;
        Integer intensidade;
        String observacoes;
    }
}
