package com.intelligenta.socialgraph.persistence;

import java.util.List;

/**
 * Three parallel per-user timelines: FIFO list, personal-importance zset,
 * everyone-importance zset. Infinispan impl emulates the ranked reads via an
 * Ickle {@code ORDER BY score DESC LIMIT} query on a cache of entries.
 */
public interface TimelineStore {
    enum Kind { FIFO, PERSONAL_IMPORTANCE, EVERYONE_IMPORTANCE }

    void push(String uid, String postId, double fifoTimestamp, double personalScore, double everyoneScore);

    List<String> range(String uid, Kind kind, int offset, int limit);

    /** Bulk fan-out for follower lists. */
    void pushMany(List<String> recipientUids, String postId,
                  double fifoTimestamp, double personalScore, double everyoneScore);
}
