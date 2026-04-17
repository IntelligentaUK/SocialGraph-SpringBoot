package com.intelligenta.socialgraph.model.search;

import java.util.List;

public record SearchResponse(List<SearchResult> results, int count, long durationMs) {
}
