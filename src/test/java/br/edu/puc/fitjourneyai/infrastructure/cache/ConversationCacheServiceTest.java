package br.edu.puc.fitjourneyai.infrastructure.cache;

import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.infrastructure.ConversationCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationCacheServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks private ConversationCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Deve retornar null no cache miss")
    void deveRetornarNullNoCacheMiss() {
        when(hashOps.entries("state:12345")).thenReturn(Map.of());
        assertThat(cacheService.getState(12345L)).isNull();
    }

    @Test
    @DisplayName("Deve retornar estado do cache hit")
    void deveRetornarEstadoDoCacheHit() {
        Map<Object, Object> data = new HashMap<>();
        data.put("userId", 1L);
        data.put("currentFlow", "ONBOARDING");
        data.put("currentStep", 3);
        data.put("partialData", "{\"nome\":\"Igor\"}");

        when(hashOps.entries("state:12345")).thenReturn(data);

        var cached = cacheService.getState(12345L);
        assertThat(cached).isNotNull();
        assertThat(cached.currentFlow()).isEqualTo(ConversationFlowType.ONBOARDING);
        assertThat(cached.currentStep()).isEqualTo(3);
        assertThat(cached.partialData()).contains("Igor");
    }

    @Test
    @DisplayName("Deve salvar estado no cache")
    void deveSalvarEstadoNoCache() {
        User user = User.builder().id(1L).telegramChatId(12345L).build();
        ConversationState state = ConversationState.builder()
                .user(user).currentFlow(ConversationFlowType.WEIGHT_CHECKIN)
                .currentStep(1).partialData("{}").updatedAt(LocalDateTime.now()).build();

        cacheService.putState(12345L, state);

        verify(hashOps).putAll(eq("state:12345"), anyMap());
        verify(redisTemplate).expire(eq("state:12345"), any(Duration.class));
    }

    @Test
    @DisplayName("Deve evictar estado do cache")
    void deveEvictarEstado() {
        cacheService.evictState(12345L);
        verify(redisTemplate).delete("state:12345");
    }

    @Test
    @DisplayName("Rate limit deve permitir primeira requisição")
    void rateLimitDevePermitirPrimeira() {
        when(valueOps.setIfAbsent(eq("rate:12345"), any(), any(Duration.class))).thenReturn(true);
        assertThat(cacheService.allowRequest(12345L)).isTrue();
    }

    @Test
    @DisplayName("Rate limit deve bloquear requisição duplicada")
    void rateLimitDeveBloquearDuplicada() {
        when(valueOps.setIfAbsent(eq("rate:12345"), any(), any(Duration.class))).thenReturn(false);
        assertThat(cacheService.allowRequest(12345L)).isFalse();
    }

    @Test
    @DisplayName("Deve degradar gracefully quando Redis falha")
    void deveDegradaGracefully() {
        when(hashOps.entries(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertThat(cacheService.getState(12345L)).isNull();
    }

    @Test
    @DisplayName("Rate limit deve permitir quando Redis falha (fail-open)")
    void rateLimitFailOpen() {
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenThrow(new RuntimeException("Redis down"));
        assertThat(cacheService.allowRequest(12345L)).isTrue();
    }
}
