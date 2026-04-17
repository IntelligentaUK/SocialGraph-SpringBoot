package com.intelligenta.socialgraph.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Creates the RediSearch vector index {@code idx:post:embedding} at startup.
 *
 * <p>Requires the Redis server to have the {@code search} module loaded
 * (Redis Stack). If the module is absent, startup fails fast with a clear error
 * rather than letting vector-search endpoints fail at query time.
 *
 * <p>{@code FT.CREATE} is idempotent: if the index already exists the
 * initializer logs at DEBUG and continues.
 *
 * <p>Index schema:
 * <pre>
 * FT.CREATE idx:post:embedding ON HASH PREFIX 1 embedding:post: SCHEMA
 *   author_uid TAG
 *   created NUMERIC SORTABLE
 *   combined_vec VECTOR HNSW 6 TYPE FLOAT32 DIM 1152 DISTANCE_METRIC COSINE
 *   text_vec     VECTOR HNSW 6 TYPE FLOAT32 DIM 1152 DISTANCE_METRIC COSINE
 * </pre>
 */
@Component
public class RedisSearchIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchIndexInitializer.class);

    public static final String INDEX_NAME = "idx:post:embedding";
    public static final String KEY_PREFIX = "embedding:post:";

    private final StringRedisTemplate redis;
    private final EmbeddingProperties props;

    public RedisSearchIndexInitializer(StringRedisTemplate redis, EmbeddingProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        redis.execute((RedisConnection connection) -> {
            createIndexIdempotent(connection);
            return null;
        });
    }

    private void createIndexIdempotent(RedisConnection conn) {
        String dim = String.valueOf(props.getVectorDim());
        byte[][] args = new byte[][] {
            bytes(INDEX_NAME),
            bytes("ON"), bytes("HASH"),
            bytes("PREFIX"), bytes("1"), bytes(KEY_PREFIX),
            bytes("SCHEMA"),
            bytes("author_uid"), bytes("TAG"),
            bytes("created"), bytes("NUMERIC"), bytes("SORTABLE"),
            bytes("combined_vec"), bytes("VECTOR"), bytes("HNSW"), bytes("6"),
                bytes("TYPE"), bytes("FLOAT32"),
                bytes("DIM"), bytes(dim),
                bytes("DISTANCE_METRIC"), bytes("COSINE"),
            bytes("text_vec"), bytes("VECTOR"), bytes("HNSW"), bytes("6"),
                bytes("TYPE"), bytes("FLOAT32"),
                bytes("DIM"), bytes(dim),
                bytes("DISTANCE_METRIC"), bytes("COSINE")
        };
        try {
            conn.execute("FT.CREATE", args);
            log.info("Created RediSearch index {} (dim={})", INDEX_NAME, dim);
        } catch (RuntimeException e) {
            if (isIndexAlreadyExists(e)) {
                log.debug("Index {} already exists; skipping creation", INDEX_NAME);
            } else if (isUnknownCommand(e)) {
                throw new IllegalStateException(
                    "Redis server does not support FT.CREATE. The RediSearch module is required. " +
                    "Run `docker compose up -d redis` (uses redis/redis-stack-server).", e);
            } else {
                throw e;
            }
        }
    }

    private static boolean isIndexAlreadyExists(Throwable t) {
        return messageMatches(t, "already exists", "indexexists");
    }

    private static boolean isUnknownCommand(Throwable t) {
        return messageMatches(t, "unknown command", "err unknown");
    }

    private static boolean messageMatches(Throwable t, String... needles) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                for (String n : needles) {
                    if (lower.contains(n)) return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
