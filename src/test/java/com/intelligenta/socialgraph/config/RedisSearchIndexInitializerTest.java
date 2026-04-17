package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.SocialGraphApplication;
import com.intelligenta.socialgraph.support.RedisStackIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SocialGraphApplication.class)
class RedisSearchIndexInitializerTest extends RedisStackIntegrationTest {

    @Autowired StringRedisTemplate redis;
    @Autowired RedisSearchIndexInitializer initializer;

    @Test
    void ftIndexIsCreatedByApplicationReadyListener() {
        List<String> indexes = redis.execute((RedisConnection conn) -> {
            Object raw = conn.execute("FT._LIST");
            return flattenToStrings(raw);
        });

        assertTrue(indexes != null && indexes.contains(RedisSearchIndexInitializer.INDEX_NAME),
            () -> "Expected FT._LIST to contain " + RedisSearchIndexInitializer.INDEX_NAME
                  + " but got: " + indexes);
    }

    @Test
    void secondCreateIsIdempotent() {
        // If the listener already created the index, re-running must not throw.
        assertDoesNotThrow(() -> initializer.onApplicationEvent(null));
    }

    @SuppressWarnings("unchecked")
    private static List<String> flattenToStrings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof byte[] b) out.add(new String(b, StandardCharsets.UTF_8));
                else if (o instanceof String s) out.add(s);
                else if (o != null) out.add(o.toString());
            }
        } else if (raw instanceof byte[] b) {
            out.add(new String(b, StandardCharsets.UTF_8));
        }
        return out;
    }
}
