package br.edu.puc.fitjourneyai.core.orchestrator;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.intent.IntentDetector;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.Message;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.MessageType;
import br.edu.puc.fitjourneyai.core.port.ConversationStateRepository;
import br.edu.puc.fitjourneyai.core.port.MessageRepository;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import br.edu.puc.fitjourneyai.infrastructure.ConversationCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestrador central de conversas do FitJourneyAI.
 * <p>
 * Responsabilidades:
 * <ol>
 *   <li>Identificar ou criar o usuário</li>
 *   <li>Carregar/criar o estado conversacional</li>
 *   <li>Detectar a intenção (cadeia de IntentDetectors)</li>
 *   <li>Resolver e delegar ao FlowHandler adequado</li>
 *   <li>Persistir o novo estado e o histórico de mensagens</li>
 *   <li>Retornar o FlowResult para o controller enviar ao Telegram</li>
 * </ol>
 * <p>
 * O envio ao Telegram é feito pelo WebhookController FORA desta transação,
 * garantindo que persistência e envio não se misturem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationOrchestrator {

    private final UserRepository userRepository;
    private final ConversationStateRepository stateRepository;
    private final MessageRepository messageRepository;
    private final FlowRegistry flowRegistry;
    private final List<IntentDetector> intentDetectors;
    private final ObjectMapper objectMapper;
    private final ConversationCacheService cacheService;

    /**
     * Ponto de entrada principal. Recebe chatId e texto bruto,
     * processa e retorna o resultado para o controller.
     *
     * @param chatId identificador do chat no Telegram
     * @param text   texto da mensagem do usuário
     * @return FlowResult com resposta e novo estado
     */
    @Transactional
    public FlowResult handleMessage(Long chatId, String text) {
        if (chatId == null) {
            log.warn("chatId nulo recebido, ignorando mensagem");
            return null;
        }

        // Rate limiting via Redis (proteção contra spam/cliques duplos)
        if (!cacheService.allowRequest(chatId)) {
            log.debug("Rate limit ativo para chatId={}", chatId);
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Identifica ou cria usuário
        User user = findOrCreateUser(chatId);
        user.setLastInteractionAt(now);
        userRepository.save(user);

        // 2. Carrega ou cria estado conversacional
        ConversationState state = findOrCreateState(user);

        // 3. Persiste mensagem do usuário
        persistMessage(user, text, MessageType.USER, now);

        // 4. Detecta intenção via cadeia
        IntentType intent = detectIntent(text);
        log.info("chatId={} | intent={} | activeFlow={} | text='{}'",
                chatId, intent, state.getCurrentFlow(), truncate(text, 50));

        // 5. Trata /cancelar — interrompe qualquer fluxo ativo
        if (intent == IntentType.CANCELAR && state.hasActiveFlow()) {
            state.reset();
            stateRepository.save(state);
            FlowResult cancelResult = FlowResult.done(
                    "Fluxo cancelado. Use /menu para ver as opções disponíveis.",
                    "Use /menu para ver as opções."
            );
            persistMessage(user, cancelResult.responseText(), MessageType.BOT, LocalDateTime.now());
            return cancelResult;
        }

        // 6. Resolve o handler: se há fluxo ativo, prioriza; senão, roteia por intenção
        FlowHandler handler = resolveHandler(state, intent, text);

        // 7. Monta contexto e delega
        FlowContext context = new FlowContext(chatId, user, state, text, intent, now);
        FlowResult result;

        try {
            result = handler.handle(context);
        } catch (Exception e) {
            log.error("Erro no handler {} para chatId={}: {}",
                    handler.getClass().getSimpleName(), chatId, e.getMessage(), e);
            result = FlowResult.done(
                    "Ops, algo deu errado aqui. Tenta de novo ou usa /menu para recomeçar.",
                    "Use /menu para ver as opções."
            );
            state.reset();
        }

        // 8. Atualiza estado conversacional
        updateState(state, result);

        // 9. Persiste resposta do bot
        if (result.responseText() != null && !result.responseText().isBlank()) {
            persistMessage(user, result.responseText(), MessageType.BOT, LocalDateTime.now());
        }

        return result;
    }

    // ========================================================================
    // MÉTODOS PRIVADOS
    // ========================================================================

    private User findOrCreateUser(Long chatId) {
        return userRepository.findByTelegramChatId(chatId)
                .orElseGet(() -> {
                    log.info("Criando novo usuário para chatId={}", chatId);
                    User newUser = User.builder()
                            .telegramChatId(chatId)
                            .onboardingConcluido(false)
                            .nudgesEnabled(true)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private ConversationState findOrCreateState(User user) {
        return stateRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    ConversationState newState = ConversationState.builder()
                            .user(user)
                            .currentFlow(ConversationFlowType.NONE)
                            .currentStep(null)
                            .partialData("{}")
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return stateRepository.save(newState);
                });
    }

    /**
     * Detecta a intenção percorrendo a cadeia de detectores ordenada por prioridade.
     */
    private IntentType detectIntent(String text) {
        return intentDetectors.stream()
                .sorted(Comparator.comparingInt(IntentDetector::priority))
                .map(detector -> detector.detect(text))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(IntentType.UNKNOWN);
    }

    /**
     * Resolve qual FlowHandler deve processar a mensagem.
     * <p>
     * Lógica:
     * 1. Se há fluxo ativo no state → usa o handler desse fluxo
     * 2. Senão, mapeia a intenção para um fluxo e resolve o handler
     * 3. Se não encontrar handler → fallback para NAVIGATION
     */
    private FlowHandler resolveHandler(ConversationState state, IntentType intent, String text) {
        if (shouldInterruptActiveFlow(state, intent, text)) {
            log.info("Entrada mudou de contexto durante fluxo ativo. Reiniciando fluxo atual={} para intent={}",
                    state.getCurrentFlow(), intent);
            state.reset();
        }

        // Fluxo ativo tem prioridade
        if (state.hasActiveFlow()) {
            Optional<FlowHandler> activeHandler = flowRegistry.resolve(state.getCurrentFlow());
            if (activeHandler.isPresent()) {
                return activeHandler.get();
            }
            // Handler não encontrado para fluxo ativo — reseta e segue
            log.warn("Handler não encontrado para fluxo ativo {}, resetando", state.getCurrentFlow());
            state.reset();
        }

        // Mapeia intenção para fluxo
        ConversationFlowType targetFlow = mapIntentToFlow(intent);
        return flowRegistry.resolve(targetFlow)
                .or(() -> flowRegistry.resolve(ConversationFlowType.NAVIGATION))
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum FlowHandler encontrado, nem mesmo NAVIGATION"));
    }

    private boolean isExplicitCommand(String text) {
        return text != null && text.trim().startsWith("/");
    }

    private boolean shouldInterruptActiveFlow(ConversationState state, IntentType intent, String text) {
        if (!state.hasActiveFlow()) {
            return false;
        }

        if (isExplicitCommand(text) && intent != IntentType.UNKNOWN && intent != IntentType.CANCELAR) {
            return true;
        }

        if (state.getCurrentFlow() == ConversationFlowType.CONFIG) {
            return shouldExitConfigFlow(intent, text);
        }

        return false;
    }

    private boolean shouldExitConfigFlow(IntentType intent, String text) {
        if (isConfigNumberAnswer(text)) {
            return false;
        }

        if (intent != IntentType.UNKNOWN && intent != IntentType.CONFIG) {
            return true;
        }

        if (isLikelyConfigAnswer(text)) {
            return false;
        }

        return isNaturalSentence(text);
    }

    private boolean isConfigNumberAnswer(String text) {
        return normalizeForRouting(text).matches("[1-6]");
    }

    private boolean isLikelyConfigAnswer(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeForRouting(text);
        String[] configTokens = {
                "coach", "amigo", "estoico", "filosofo", "sargento", "drill", "militar",
                "atleta", "elite", "monge", "guerreiro", "cientista", "ciencia",
                "leve", "gentil", "suave", "moderado", "medio", "equilibrado",
                "intenso", "maximo", "hardcore", "pesado"
        };

        for (String token : configTokens) {
            if (normalized.matches(".*\\b" + token + "\\b.*")) {
                return true;
            }
        }

        return false;
    }

    private boolean isNaturalSentence(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String trimmed = text.trim();
        int words = trimmed.split("\\s+").length;
        return words >= 5 || trimmed.contains("?") || trimmed.contains("!");
    }

    private String normalizeForRouting(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Mapeia uma intenção detectada para o tipo de fluxo correspondente.
     */
    private ConversationFlowType mapIntentToFlow(IntentType intent) {
        return switch (intent) {
            case START -> ConversationFlowType.ONBOARDING;
            case REGISTRO_PESO -> ConversationFlowType.WEIGHT_CHECKIN;
            case REGISTRO, REGISTRO_MEDIDAS -> ConversationFlowType.MEASUREMENTS_CHECKIN;
            case TREINO_FEITO -> ConversationFlowType.ACTIVITY_REGISTRATION;
            case TREINO -> ConversationFlowType.WORKOUT_GENERATION;
            case PROGRESSO -> ConversationFlowType.PROGRESS;
            case RESUMO -> ConversationFlowType.SUMMARY;
            case CONVERSA -> ConversationFlowType.CONTEXTUAL_CONVERSATION;
            case MENU, AJUDA, CANCELAR -> ConversationFlowType.NAVIGATION;
            case CONFIG -> ConversationFlowType.CONFIG;
            case UNKNOWN -> ConversationFlowType.CONTEXTUAL_CONVERSATION;
        };
    }

    /**
     * Atualiza o ConversationState com os dados do FlowResult.
     */
    private void updateState(ConversationState state, FlowResult result) {
        state.setCurrentFlow(result.nextFlow());
        state.setCurrentStep(result.nextStep());

        if (result.stateData() != null && !result.stateData().isEmpty()) {
            try {
                state.setPartialData(objectMapper.writeValueAsString(result.stateData()));
            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar stateData: {}", e.getMessage());
                state.setPartialData("{}");
            }
        } else if (result.nextFlow() == ConversationFlowType.NONE) {
            state.setPartialData("{}");
        }

        stateRepository.save(state);

        // Atualiza cache Redis (write-through)
        Long chatId = state.getUser().getTelegramChatId();
        if (chatId != null) {
            cacheService.putState(chatId, state);
        }
    }

    private void persistMessage(User user, String text, MessageType type, LocalDateTime timestamp) {
        if (text == null || text.isBlank()) return;

        Message msg = Message.builder()
                .user(user)
                .conteudo(text)
                .tipo(type)
                .dataHora(timestamp)
                .build();
        messageRepository.save(msg);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
