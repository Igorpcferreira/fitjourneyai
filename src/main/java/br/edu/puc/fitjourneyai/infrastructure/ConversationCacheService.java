package br.edu.puc.fitjourneyai.infrastructure;

import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Camada de cache Redis para estado conversacional e rate limiting.
 * <p>
 * Padrão Cache-Aside:
 * <ul>
 *   <li>READ: Redis primeiro → se miss, lê PostgreSQL e popula Redis</li>
 *   <li>WRITE: Escreve em ambos (PostgreSQL é source of truth)</li>
 *   <li>TTL: 30 minutos (estados inativos expiram automaticamente)</li>
 * </ul>
 * <p>
 * Se o Redis estiver indisponível, o sistema funciona normalmente
 * apenas com PostgreSQL (degradação graceful).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCacheService {

    private static final String STATE_PREFIX = "state:";
    private static final String RATE_PREFIX = "rate:";
    private static final Duration STATE_TTL = Duration.ofMinutes(30);
    private static final Duration RATE_WINDOW = Duration.ofSeconds(2);

    private final RedisTemplate<String, Object> redisTemplate;

    // ========================================================================
    // CACHE DE CONVERSATION STATE
    // ========================================================================

    /**
     * Busca estado do cache. Retorna null se não encontrar (cache miss).
     */
    public CachedState getState(Long chatId) {
        try {
            String key = STATE_PREFIX + chatId;
            Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
            if (data.isEmpty()) return null;

            return CachedState.fromMap(data);
        } catch (Exception e) {
            log.debug("Redis cache miss (erro) para chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * Salva estado no cache com TTL de 30 minutos.
     */
    public void putState(Long chatId, ConversationState state) {
        try {
            String key = STATE_PREFIX + chatId;
            Map<String, Object> data = new HashMap<>();
            data.put("userId", state.getUser().getId());
            data.put("currentFlow", state.getCurrentFlow() != null ? state.getCurrentFlow().name() : "NONE");
            data.put("currentStep", state.getCurrentStep());
            data.put("partialData", state.getPartialData() != null ? state.getPartialData() : "{}");

            redisTemplate.opsForHash().putAll(key, data);
            redisTemplate.expire(key, STATE_TTL);

            log.debug("Cache atualizado para chatId={}, flow={}", chatId, state.getCurrentFlow());
        } catch (Exception e) {
            log.debug("Falha ao atualizar cache Redis para chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Remove estado do cache (ex: após reset ou onboarding completo).
     */
    public void evictState(Long chatId) {
        try {
            redisTemplate.delete(STATE_PREFIX + chatId);
        } catch (Exception e) {
            log.debug("Falha ao evictar cache para chatId={}: {}", chatId, e.getMessage());
        }
    }

    // ========================================================================
    // RATE LIMITING
    // ========================================================================

    /**
     * Verifica se o usuário pode enviar mensagem (rate limit: 1 msg a cada 2s).
     * Protege contra spam e cliques duplos no Telegram.
     *
     * @return true se pode enviar, false se está em rate limit
     */
    public boolean allowRequest(Long chatId) {
        try {
            String key = RATE_PREFIX + chatId;
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", RATE_WINDOW);
            return Boolean.TRUE.equals(isNew);
        } catch (Exception e) {
            // Se Redis falhar, permite (fail-open)
            return true;
        }
    }

    // ========================================================================
    // CACHED STATE DTO
    // ========================================================================

    /**
     * DTO leve para estado em cache (sem entidade JPA completa).
     */
    public record CachedState(
            Long userId,
            ConversationFlowType currentFlow,
            Integer currentStep,
            String partialData
    ) {
        public boolean hasActiveFlow() {
            return currentFlow != null && currentFlow != ConversationFlowType.NONE;
        }

        static CachedState fromMap(Map<Object, Object> data) {
            Long userId = data.get("userId") instanceof Number n ? n.longValue() : null;
            String flowStr = data.get("currentFlow") != null ? data.get("currentFlow").toString() : "NONE";
            ConversationFlowType flow;
            try {
                flow = ConversationFlowType.valueOf(flowStr);
            } catch (Exception e) {
                flow = ConversationFlowType.NONE;
            }
            Integer step = data.get("currentStep") instanceof Number n ? n.intValue() : null;
            String partial = data.get("partialData") != null ? data.get("partialData").toString() : "{}";
            return new CachedState(userId, flow, step, partial);
        }
    }
}
