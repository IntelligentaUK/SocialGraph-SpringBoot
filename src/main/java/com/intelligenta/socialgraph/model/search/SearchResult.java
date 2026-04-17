package com.intelligenta.socialgraph.model.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Single hit from {@code /api/search/question} or {@code /api/search/ai}.
 * {@code score} is the RediSearch KNN distance (cosine, lower is more
 * similar).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(
    String id,
    String uid,
    String type,
    String content,
    String url,
    List<String> imageUrls,
    String created,
    double score) {
}
