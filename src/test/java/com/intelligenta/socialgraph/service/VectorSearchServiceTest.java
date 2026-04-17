package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.SocialGraphApplication;
import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.model.search.SearchResult;
import com.intelligenta.socialgraph.support.RedisStackIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = SocialGraphApplication.class)
class VectorSearchServiceTest extends RedisStackIntegrationTest {

    // @MockitoBean fires after context startup, but RedisSearchIndexInitializer's
    // onApplicationEvent runs DURING startup and calls embeddingProvider.vectorDim()
    // — if the mock returns 0 the FT.CREATE fails. Spy over the real sidecar bean
    // so index creation uses the real 1152; only embedText behaviour needs mocking.
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    EmbeddingProvider embeddingProvider;

    @Autowired StringRedisTemplate redis;
    @Autowired RedisTemplate<String, byte[]> binaryRedis;
    @Autowired EmbeddingProperties props;
    @Autowired VectorSearchService service;

    @BeforeEach
    void clearKeys() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        // Recreate index because flushdb drops it
        try {
            new com.intelligenta.socialgraph.config.RedisSearchIndexInitializer(redis, embeddingProvider)
                .onApplicationEvent(null);
        } catch (Exception ignored) {
            // Index may still exist; recreate tolerated
        }
    }

    @Test
    void questionSearchRanksPostsByCombinedVectorKnnWithin7DayWindow() {
        long now = Instant.now().getEpochSecond();
        long old = now - Duration.ofDays(10).toSeconds();

        // post-cat: combined_vec close to query vector
        writePostAndEmbedding("post-cat", "u1", "a cat on a mat", "https://cdn/cat.png",
            unitVec(1152, 0), now);
        // post-dog: orthogonal direction
        writePostAndEmbedding("post-dog", "u2", "a dog in the park", "https://cdn/dog.png",
            unitVec(1152, 1), now);
        // post-old: same direction as cat but OUTSIDE 7-day window
        writePostAndEmbedding("post-old", "u3", "old cat", "https://cdn/old.png",
            unitVec(1152, 0), old);

        // Query embedding is identical to post-cat's combined_vec so it should rank first.
        org.mockito.Mockito.doReturn(unitVec(1152, 0))
            .when(embeddingProvider).embedText(org.mockito.ArgumentMatchers.any());

        List<SearchResult> results = service.questionSearch("feline", 10);

        assertFalse(results.isEmpty(), "expected at least one result");
        assertEquals("post-cat", results.get(0).id(),
            "closest post must come first (got: " + results + ")");
        assertFalse(results.stream().anyMatch(r -> "post-old".equals(r.id())),
            "post outside the 7-day window must be excluded");
    }

    @Test
    void aiTextSearchUsesTextVectorField() {
        long now = Instant.now().getEpochSecond();
        writePostAndEmbeddingText("post-text", "u1", "just text content", now, unitVec(1152, 5));

        org.mockito.Mockito.doReturn(unitVec(1152, 5))
            .when(embeddingProvider).embedText(org.mockito.ArgumentMatchers.any());

        List<SearchResult> results = service.aiTextSearch("content", 10);

        assertFalse(results.isEmpty());
        assertEquals("post-text", results.get(0).id());
        assertEquals("just text content", results.get(0).content());
    }

    private void writePostAndEmbedding(String postId, String uid, String content,
                                       String url, float[] combinedVec, long created) {
        redis.opsForHash().putAll("post:" + postId, Map.of(
            "id", postId, "uid", uid, "type", "photo",
            "content", content, "url", url, "imageCount", "1",
            "created", String.valueOf(created)));
        redis.opsForList().rightPush("post:" + postId + ":images", url);

        String key = com.intelligenta.socialgraph.config.RedisSearchIndexInitializer.keyPrefix("sidecar", 1152) + postId;
        redis.opsForHash().put(key, "author_uid", uid);
        redis.opsForHash().put(key, "created", String.valueOf(created));
        redis.opsForHash().put(key, "gemma_summary", content);
        binaryRedis.opsForHash().put(key, "combined_vec", EmbeddingWorker.toLeBytes(combinedVec));
        binaryRedis.opsForHash().put(key, "text_vec", EmbeddingWorker.toLeBytes(combinedVec));
        redis.expire(key, 691_200L, TimeUnit.SECONDS);
    }

    private void writePostAndEmbeddingText(String postId, String uid, String content,
                                           long created, float[] textVec) {
        redis.opsForHash().putAll("post:" + postId, Map.of(
            "id", postId, "uid", uid, "type", "text",
            "content", content,
            "created", String.valueOf(created)));

        String key = com.intelligenta.socialgraph.config.RedisSearchIndexInitializer.keyPrefix("sidecar", 1152) + postId;
        redis.opsForHash().put(key, "author_uid", uid);
        redis.opsForHash().put(key, "created", String.valueOf(created));
        redis.opsForHash().put(key, "gemma_summary", content);
        binaryRedis.opsForHash().put(key, "text_vec", EmbeddingWorker.toLeBytes(textVec));
        // combined_vec intentionally absent for text-only posts
        redis.expire(key, 691_200L, TimeUnit.SECONDS);
    }

    /** Returns a unit vector with a single 1.0 at position {@code axis}. */
    private static float[] unitVec(int dim, int axis) {
        float[] v = new float[dim];
        v[axis] = 1.0f;
        return v;
    }
}
