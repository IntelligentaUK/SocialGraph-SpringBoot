package com.intelligenta.socialgraph.persistence;

/**
 * Global per-user counters: {@code photos}, {@code videos}, {@code posts}.
 * Redis implementation uses {@code HINCRBY} on three flat hashes. Infinispan
 * backs them with strong counters inside the post-create transaction so they
 * stay accurate under concurrent writes across the cluster.
 */
public interface CounterStore {
    enum Kind { PHOTOS, VIDEOS, POSTS }

    void increment(Kind kind, String uid, long delta);
    long get(Kind kind, String uid);
}
