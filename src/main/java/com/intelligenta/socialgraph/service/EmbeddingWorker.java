package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.ai.AudioSummarizer;
import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.ai.VideoSummarizer;
import com.intelligenta.socialgraph.ai.VisualSummarizer;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.config.RedisSearchIndexInitializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Background consumer for the {@code embedding:queue} stream. Reads new post
 * events via {@code XREADGROUP}, calls the sidecar for (optionally) a Gemma
 * summary + the two SigLIP-2 vectors, then HSETs the resulting fields onto
 * {@code embedding:post:<id>} with an 8-day TTL. Failed messages are retried
 * up to {@code embedding.dlq-max-retries} times before being redirected to
 * {@code embedding:queue:dlq}.
 */
@Component
public class EmbeddingWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);
    static final String STREAM = "embedding:queue";
    static final String DLQ_STREAM = "embedding:queue:dlq";
    static final String GROUP = "embed-workers";
    static final String CONSUMER = "worker-1";

    private final StringRedisTemplate redis;
    private final RedisTemplate<String, byte[]> binaryRedis;
    private final EmbeddingProvider embeddingProvider;
    private final VisualSummarizer summarizer;
    private final AudioSummarizer audioSummarizer;
    private final VideoSummarizer videoSummarizer;
    private final EmbeddingProperties props;
    private final Map<String, Integer> retries = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private Thread worker;

    public EmbeddingWorker(StringRedisTemplate redis,
                           RedisTemplate<String, byte[]> binaryRedisTemplate,
                           EmbeddingProvider embeddingProvider,
                           VisualSummarizer summarizer,
                           AudioSummarizer audioSummarizer,
                           VideoSummarizer videoSummarizer,
                           EmbeddingProperties props) {
        this.redis = redis;
        this.binaryRedis = binaryRedisTemplate;
        this.embeddingProvider = embeddingProvider;
        this.summarizer = summarizer;
        this.audioSummarizer = audioSummarizer;
        this.videoSummarizer = videoSummarizer;
        this.props = props;
    }

    @PostConstruct
    void start() {
        ensureGroup();
        worker = new Thread(this::loop, "embedding-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("EmbeddingWorker started; consuming {} on group {}", STREAM, GROUP);
    }

    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        if (worker != null) {
            worker.join(Duration.ofSeconds(10).toMillis());
        }
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(STREAM, ReadOffset.latest(), GROUP);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (!msg.contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    void loop() {
        Consumer consumer = Consumer.from(GROUP, CONSUMER);
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    consumer,
                    StreamReadOptions.empty().block(Duration.ofSeconds(5)).count(10),
                    StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) continue;
                for (MapRecord<String, Object, Object> rec : records) {
                    try {
                        process(rec);
                        retries.remove(rec.getId().getValue());
                    } catch (Exception e) {
                        onFailure(rec, e);
                    }
                }
            } catch (Exception e) {
                if (!running) break;
                log.error("embedding worker loop error", e);
                sleep(1000);
            }
        }
        log.info("EmbeddingWorker stopping");
    }

    void process(MapRecord<String, Object, Object> rec) {
        String postId = String.valueOf(rec.getValue().get("postId"));
        String uid = String.valueOf(rec.getValue().get("authorUid"));

        Map<Object, Object> post = redis.opsForHash().entries("post:" + postId);
        if (post.isEmpty()) {
            log.warn("skipping embedding for missing post {}", postId);
            redis.opsForStream().acknowledge(STREAM, GROUP, rec.getId());
            return;
        }

        String content = post.get("content") == null ? "" : String.valueOf(post.get("content"));
        String created = String.valueOf(post.get("created"));
        String type = post.get("type") == null ? "" : String.valueOf(post.get("type"));
        String url = post.get("url") == null ? "" : String.valueOf(post.get("url"));

        List<String> images = redis.opsForList().range(
            "post:" + postId + ":images", 0, props.getImagesForEmbedding() - 1);
        if (images == null) images = List.of();

        String gemmaSummary = images.isEmpty() ? "" : summarizer.summarize(content, images);
        String audioSummary = "audio".equals(type) && !url.isBlank()
            ? audioSummarizer.summarize(content, url) : "";
        String videoSummary = "video".equals(type) && !url.isBlank()
            ? videoSummarizer.summarize(content, url) : "";

        float[] combined;
        if (images.isEmpty()) {
            combined = null;
        } else {
            String combinedText = gemmaSummary.isBlank()
                ? (content.isBlank() ? " " : content) : gemmaSummary;
            combined = embeddingProvider.embedImageAndText(images.get(0), combinedText)
                .orElseGet(() -> embeddingProvider.embedText(combinedText));
        }

        float[] text = embeddingProvider.embedText(
            concatenateForTextVec(content, gemmaSummary, audioSummary, videoSummary));

        String key = RedisSearchIndexInitializer.keyPrefix(
            embeddingProvider.providerKey(), embeddingProvider.vectorDim()) + postId;
        redis.opsForHash().put(key, "author_uid", uid);
        redis.opsForHash().put(key, "created", created);
        if (!gemmaSummary.isBlank()) {
            redis.opsForHash().put(key, "gemma_summary", gemmaSummary);
        }
        if (!audioSummary.isBlank()) {
            redis.opsForHash().put(key, "audio_summary", audioSummary);
        }
        if (!videoSummary.isBlank()) {
            redis.opsForHash().put(key, "video_summary", videoSummary);
        }
        if (combined != null) {
            binaryRedis.opsForHash().put(key, "combined_vec", toLeBytes(combined));
        }
        binaryRedis.opsForHash().put(key, "text_vec", toLeBytes(text));
        redis.expire(key, props.getEmbeddingTtlSeconds(), TimeUnit.SECONDS);

        redis.opsForStream().acknowledge(STREAM, GROUP, rec.getId());
    }

    private static String concatenateForTextVec(String content, String gemma, String audio, String video) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, content);
        appendIfPresent(sb, gemma);
        appendIfPresent(sb, audio);
        appendIfPresent(sb, video);
        return sb.length() == 0 ? " " : sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String s) {
        if (s == null || s.isBlank()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(s);
    }

    private void onFailure(MapRecord<String, Object, Object> rec, Exception e) {
        int attempts = retries.merge(rec.getId().getValue(), 1, Integer::sum);
        log.warn("embedding attempt {} failed for {} (post={})",
            attempts, rec.getId(), rec.getValue().get("postId"), e);
        if (attempts >= props.getDlqMaxRetries()) {
            redis.opsForStream().add(MapRecord.create(DLQ_STREAM, Map.of(
                "postId", String.valueOf(rec.getValue().get("postId")),
                "authorUid", String.valueOf(rec.getValue().get("authorUid")),
                "failure", e.getClass().getSimpleName(),
                "message", String.valueOf(e.getMessage()),
                "attempts", String.valueOf(attempts))));
            redis.opsForStream().acknowledge(STREAM, GROUP, rec.getId());
            retries.remove(rec.getId().getValue());
        }
    }

    /**
     * Pack a float32 array into little-endian bytes as required by RediSearch
     * VECTOR fields with {@code TYPE FLOAT32}.
     */
    public static byte[] toLeBytes(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) bb.putFloat(f);
        return bb.array();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
