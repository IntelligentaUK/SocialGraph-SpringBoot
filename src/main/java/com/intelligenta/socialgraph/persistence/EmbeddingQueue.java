package com.intelligenta.socialgraph.persistence;

import java.util.function.BiConsumer;

/**
 * At-least-once queue for the background embedding pipeline. Redis impl uses
 * Redis Streams + XREADGROUP; Infinispan impl uses a distributed cache keyed
 * by a monotonic {@code CounterManager} sequence, with a {@code @ClientListener}
 * for consumption and a DLQ cache for failures. Ordering is preserved in both.
 */
public interface EmbeddingQueue {

    void enqueue(String postId, String authorUid);

    /**
     * Register a consumer. Called once at startup; the implementation spins a
     * worker thread that delivers entries to the handler until {@link #stop}
     * is called. The handler receives (postId, authorUid).
     */
    void consume(BiConsumer<String, String> handler);

    void stop();

    /** Send a failed entry to the dead-letter queue. */
    void deadLetter(String postId, String authorUid, String reason);

    /** True when the queue is wired to a real backend (i.e. provider-gated). */
    boolean enabled();
}
