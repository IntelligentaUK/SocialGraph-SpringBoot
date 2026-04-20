package com.intelligenta.socialgraph.persistence;

import com.intelligenta.socialgraph.model.search.SearchResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Vector-search index for post embeddings. Redis impl wraps RediSearch
 * {@code FT.CREATE} + {@code FT.SEARCH}; Infinispan impl uses Protobuf-
 * indexed entities with Ickle k-NN queries. Both honour a time-window
 * filter (posts younger than N days) and return top-k by cosine distance.
 */
public interface EmbeddingIndexStore {

    void write(String postId, Map<String, Object> fields, Duration ttl);

    List<SearchResult> knn(String queryVectorField, float[] queryVector, int k, long withinSeconds);

    /** Recreate/ensure the index at startup. Called from ApplicationReadyEvent listeners. */
    void ensureIndex();
}
