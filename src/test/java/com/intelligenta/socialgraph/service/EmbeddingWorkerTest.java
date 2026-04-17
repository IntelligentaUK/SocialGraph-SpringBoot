package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.ai.VisualSummarizer;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingWorkerTest {

    @Mock StringRedisTemplate redis;
    @Mock HashOperations<String, Object, Object> hashOperations;
    @Mock ListOperations<String, String> listOperations;
    @Mock @SuppressWarnings("rawtypes") StreamOperations streamOperations;

    @Mock RedisTemplate<String, byte[]> binaryRedis;
    @Mock @SuppressWarnings("rawtypes") HashOperations binaryHashOperations;

    @Mock EmbeddingProvider embeddingProvider;
    @Mock VisualSummarizer summarizer;

    private EmbeddingProperties props;
    private EmbeddingWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForHash()).thenReturn(hashOperations);
        lenient().when(redis.opsForList()).thenReturn(listOperations);
        lenient().when(redis.opsForStream()).thenReturn(streamOperations);
        lenient().when(binaryRedis.opsForHash()).thenReturn(binaryHashOperations);
        lenient().when(hashOperations.entries(org.mockito.ArgumentMatchers.<String>any())).thenReturn(Map.of());
        props = new EmbeddingProperties();
        worker = new EmbeddingWorker(redis, binaryRedis, embeddingProvider, summarizer, props);
    }

    @Test
    void processWritesTextOnlyEmbeddingForTextPost() {
        Map<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-t");
        post.put("uid", "u-1");
        post.put("content", "hello world");
        post.put("created", "1700000000");
        when(hashOperations.entries("post:post-t")).thenReturn(post);
        when(listOperations.range("post:post-t:images", 0, props.getImagesForEmbedding() - 1))
            .thenReturn(List.of());

        float[] textVec = fakeVec(1152, 0.1f);
        when(embeddingProvider.embedText("hello world")).thenReturn(textVec);

        @SuppressWarnings({"unchecked", "rawtypes"})
        MapRecord<String, Object, Object> rec = (MapRecord) MapRecord.create(
            EmbeddingWorker.STREAM, Map.of("postId", (Object) "post-t", "authorUid", "u-1"))
            .withId(RecordId.of("1-0"));

        worker.process(rec);

        verify(summarizer, never()).summarize(any(), anyList());
        verify(embeddingProvider, never()).embedImageAndText(any(), any());
        verify(hashOperations).put("embedding:post:post-t", "author_uid", "u-1");
        verify(hashOperations).put("embedding:post:post-t", "created", "1700000000");

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(binaryHashOperations).put(eq("embedding:post:post-t"), eq("text_vec"), bytesCaptor.capture());
        assertArrayEquals(textVec, leBytesToFloats(bytesCaptor.getValue()), 1e-6f);
        verify(binaryHashOperations, never()).put(eq("embedding:post:post-t"), eq("combined_vec"), any());

        verify(redis).expire("embedding:post:post-t", props.getEmbeddingTtlSeconds(), TimeUnit.SECONDS);
        verify(streamOperations).acknowledge(EmbeddingWorker.STREAM, EmbeddingWorker.GROUP, rec.getId());
    }

    @Test
    void processWritesCombinedAndTextEmbeddingsForImagePost() {
        Map<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-p");
        post.put("uid", "u-2");
        post.put("content", "a nice pic");
        post.put("created", "1700000100");
        when(hashOperations.entries("post:post-p")).thenReturn(post);
        when(listOperations.range("post:post-p:images", 0, props.getImagesForEmbedding() - 1))
            .thenReturn(List.of("https://cdn/a.png", "https://cdn/b.png"));

        when(summarizer.summarize(eq("a nice pic"), eq(List.of("https://cdn/a.png", "https://cdn/b.png"))))
            .thenReturn("a picture of something");
        float[] combined = fakeVec(1152, 0.2f);
        float[] textVec = fakeVec(1152, 0.3f);
        when(embeddingProvider.embedImageAndText("https://cdn/a.png", "a picture of something"))
            .thenReturn(java.util.Optional.of(combined));
        when(embeddingProvider.embedText("a nice pic")).thenReturn(textVec);

        @SuppressWarnings({"unchecked", "rawtypes"})
        MapRecord<String, Object, Object> rec = (MapRecord) MapRecord.create(
            EmbeddingWorker.STREAM, Map.of("postId", (Object) "post-p", "authorUid", "u-2"))
            .withId(RecordId.of("2-0"));

        worker.process(rec);

        verify(hashOperations).put("embedding:post:post-p", "gemma_summary", "a picture of something");
        verify(binaryHashOperations).put(eq("embedding:post:post-p"), eq("combined_vec"), any(byte[].class));
        verify(binaryHashOperations).put(eq("embedding:post:post-p"), eq("text_vec"), any(byte[].class));
        verify(streamOperations).acknowledge(EmbeddingWorker.STREAM, EmbeddingWorker.GROUP, rec.getId());
    }

    @Test
    void missingPostIsAckedAndSkipped() {
        when(hashOperations.entries("post:gone")).thenReturn(Map.of());

        @SuppressWarnings({"unchecked", "rawtypes"})
        MapRecord<String, Object, Object> rec = (MapRecord) MapRecord.create(
            EmbeddingWorker.STREAM, Map.of("postId", (Object) "gone", "authorUid", "u-3"))
            .withId(RecordId.of("3-0"));

        worker.process(rec);

        verify(streamOperations).acknowledge(EmbeddingWorker.STREAM, EmbeddingWorker.GROUP, rec.getId());
        verify(embeddingProvider, never()).embedText(any());
    }

    @Test
    void toLeBytesRoundTripsCorrectly() {
        float[] v = new float[]{1.5f, -2.25f, 0.0f, 3.125f};
        byte[] bytes = EmbeddingWorker.toLeBytes(v);
        assertEquals(16, bytes.length);
        assertArrayEquals(v, leBytesToFloats(bytes), 0f);
    }

    private static float[] fakeVec(int dim, float seed) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) v[i] = seed + i * 1e-4f;
        return v;
    }

    private static float[] leBytesToFloats(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[bytes.length / 4];
        for (int i = 0; i < out.length; i++) out[i] = bb.getFloat();
        return out;
    }
}
