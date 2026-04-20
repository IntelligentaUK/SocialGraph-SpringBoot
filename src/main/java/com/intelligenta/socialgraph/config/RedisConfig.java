package com.intelligenta.socialgraph.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Spring Data Redis.
 *
 * <p>The three templates ({@link StringRedisTemplate}, {@link RedisTemplate})
 * are always wired — they operate over whatever endpoint Lettuce is pointed at
 * (Redis Stack by default; Infinispan's RESP endpoint when
 * {@code persistence.provider=infinispan} and {@code client-mode=resp}, via
 * {@link PersistenceEnvironmentPostProcessor}).
 *
 * <p>The RediSearch-specific client and binary connection are gated on
 * {@code persistence.provider=redis} because Infinispan's RESP endpoint does
 * not implement the {@code FT.*} command family. Phase I-I wires an
 * Infinispan-native replacement; until then, vector search endpoints are
 * absent when Infinispan is the selected provider.
 */
@Configuration
public class RedisConfig {

    private RedisClient searchClient;
    private StatefulRedisConnection<byte[], byte[]> searchConnection;

    /**
     * Dedicated Lettuce client for RediSearch FT.* queries. Uses a byte-array
     * codec so vector PARAMS can be passed as raw bytes (RediSearch requires
     * float32 vectors be sent as binary blobs).
     */
    @Bean
    @ConditionalOnProperty(prefix = "persistence", name = "provider", havingValue = "redis", matchIfMissing = true)
    public RedisClient redisSearchClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        RedisURI.Builder uri = RedisURI.Builder.redis(host, port);
        if (password != null && !password.isEmpty()) {
            uri.withPassword(password.toCharArray());
        }
        this.searchClient = RedisClient.create(uri.build());
        return searchClient;
    }

    @Bean
    @ConditionalOnProperty(prefix = "persistence", name = "provider", havingValue = "redis", matchIfMissing = true)
    public StatefulRedisConnection<byte[], byte[]> redisSearchBinaryConnection(RedisClient client) {
        this.searchConnection = client.connect(ByteArrayCodec.INSTANCE);
        return searchConnection;
    }

    @PreDestroy
    void closeSearchClient() {
        if (searchConnection != null) {
            searchConnection.close();
        }
        if (searchClient != null) {
            searchClient.shutdown();
        }
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * Binary-value template used for storing raw float32 vectors as Redis hash fields
     * (RediSearch VECTOR fields require the raw little-endian byte layout). Keys and
     * hash field names stay UTF-8; only values are preserved as-is.
     */
    @Bean
    public RedisTemplate<String, byte[]> binaryRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        return template;
    }
}
