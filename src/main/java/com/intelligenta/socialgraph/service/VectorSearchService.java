package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.config.RedisSearchIndexInitializer;
import com.intelligenta.socialgraph.model.search.SearchResult;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.QueryDialects;
import io.lettuce.core.search.arguments.SearchArgs;
import io.lettuce.core.search.arguments.SortByArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side KNN vector search backed by RediSearch FT.SEARCH. Performs a
 * numeric prefilter on {@code created} (7-day window) combined with a KNN
 * query on either {@code combined_vec} (question search; multimodal) or
 * {@code text_vec} (AI text search; caption-only).
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);
    private static final String INDEX = RedisSearchIndexInitializer.INDEX_NAME;
    private static final String KEY_PREFIX = RedisSearchIndexInitializer.KEY_PREFIX;

    private final StringRedisTemplate redis;
    private final StatefulRedisConnection<byte[], byte[]> searchConnection;
    private final EmbeddingClient sidecar;
    private final EmbeddingProperties props;

    public VectorSearchService(StringRedisTemplate redis,
                               StatefulRedisConnection<byte[], byte[]> searchConnection,
                               EmbeddingClient sidecar,
                               EmbeddingProperties props) {
        this.redis = redis;
        this.searchConnection = searchConnection;
        this.sidecar = sidecar;
        this.props = props;
    }

    /** Multimodal: KNN over combined image+Gemma-summary vectors. */
    public List<SearchResult> questionSearch(String query, int limit) {
        return knn(query, limit, "combined_vec");
    }

    /** Text-only: KNN over SigLIP-2 caption embeddings. */
    public List<SearchResult> aiTextSearch(String query, int limit) {
        return knn(query, limit, "text_vec");
    }

    private List<SearchResult> knn(String query, int limit, String vectorField) {
        int k = Math.max(1, Math.min(limit, props.getSearchLimitMax()));
        float[] qvec = sidecar.embedText(query);
        byte[] qbytes = EmbeddingWorker.toLeBytes(qvec);

        long now = Instant.now().getEpochSecond();
        long start = now - Duration.ofDays(props.getSearchWindowDays()).toSeconds();
        String expr = String.format("@created:[%d %d]=>[KNN %d @%s $qv AS score]",
            start, now, k, vectorField);

        SearchArgs<byte[], byte[]> args = SearchArgs.<byte[], byte[]>builder()
            .dialect(QueryDialects.DIALECT2)
            .sortBy(SortByArgs.<byte[]>builder()
                .attribute(bytes("score"))
                .build())
            .returnField(bytes("__key"))
            .returnField(bytes("score"))
            .limit(0, k)
            .param(bytes("qv"), qbytes)
            .build();

        SearchReply<byte[], byte[]> reply;
        try {
            reply = searchConnection.sync().ftSearch(bytes(INDEX), bytes(expr), args);
        } catch (RedisCommandExecutionException e) {
            log.warn("FT.SEARCH failed: {}", e.getMessage());
            return List.of();
        }

        return hydrate(reply);
    }

    private List<SearchResult> hydrate(SearchReply<byte[], byte[]> reply) {
        if (reply == null) return List.of();
        List<SearchReply.SearchResult<byte[], byte[]>> results = reply.getResults();
        if (results == null || results.isEmpty()) return List.of();

        List<SearchResult> out = new ArrayList<>();
        for (SearchReply.SearchResult<byte[], byte[]> doc : results) {
            byte[] idBytes = doc.getId();
            if (idBytes == null) continue;
            String key = new String(idBytes, StandardCharsets.UTF_8);
            if (!key.startsWith(KEY_PREFIX)) continue;
            String postId = key.substring(KEY_PREFIX.length());

            double score = 1.0;
            Map<byte[], byte[]> fields = doc.getFields();
            if (fields != null) {
                for (Map.Entry<byte[], byte[]> e : fields.entrySet()) {
                    if ("score".equals(new String(e.getKey(), StandardCharsets.UTF_8))) {
                        try {
                            score = Double.parseDouble(new String(e.getValue(), StandardCharsets.UTF_8));
                        } catch (NumberFormatException ignored) {
                            // keep 1.0
                        }
                    }
                }
            }

            Map<Object, Object> post = redis.opsForHash().entries("post:" + postId);
            if (post.isEmpty()) continue;
            List<String> images = redis.opsForList().range("post:" + postId + ":images", 0, -1);

            out.add(new SearchResult(
                postId,
                (String) post.get("uid"),
                (String) post.get("type"),
                (String) post.get("content"),
                (String) post.get("url"),
                images == null || images.isEmpty() ? null : images,
                (String) post.get("created"),
                score));
        }
        return out;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
