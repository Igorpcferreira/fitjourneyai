package br.edu.puc.fitjourneyai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuração do Redis para cache de estado conversacional e rate limiting.
 * <p>
 * Redis é COMPLEMENTO do PostgreSQL:
 * <ul>
 *   <li>Cache de ConversationState (hot path, lido a cada mensagem)</li>
 *   <li>Rate limiting por chatId (proteção contra spam)</li>
 *   <li>Cache de respostas IA recentes (evita chamadas duplicadas)</li>
 * </ul>
 * Dados persistentes (Users, Workouts, Measurements) ficam no PostgreSQL.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
