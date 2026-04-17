package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.model.search.SearchRequest;
import com.intelligenta.socialgraph.model.search.SearchResponse;
import com.intelligenta.socialgraph.model.search.SearchResult;
import com.intelligenta.socialgraph.security.AuthenticatedUser;
import com.intelligenta.socialgraph.service.VectorSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vector-search endpoints over the last 7 days of posts.
 *
 * <ul>
 *   <li>{@code POST /api/search/question} — KNN over the combined (image +
 *   Gemma summary) vectors.</li>
 *   <li>{@code POST /api/search/ai} — KNN over the text-only SigLIP-2
 *   caption vectors.</li>
 * </ul>
 *
 * Both accept a {@link SearchRequest} with {@code query} and an optional
 * {@code limit} (default 20, max 100).
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final VectorSearchService search;
    private final EmbeddingProperties props;

    public SearchController(VectorSearchService search, EmbeddingProperties props) {
        this.search = search;
        this.props = props;
    }

    @PostMapping("/question")
    public ResponseEntity<SearchResponse> question(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid SearchRequest req) {
        return runSearch(search.questionSearch(req.query(), resolveLimit(req)));
    }

    @PostMapping("/ai")
    public ResponseEntity<SearchResponse> aiSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid SearchRequest req) {
        return runSearch(search.aiTextSearch(req.query(), resolveLimit(req)));
    }

    private int resolveLimit(SearchRequest req) {
        return req.limit() == null ? props.getSearchLimitDefault() : req.limit();
    }

    private ResponseEntity<SearchResponse> runSearch(List<SearchResult> results) {
        long t = System.currentTimeMillis();
        return ResponseEntity.ok(new SearchResponse(results, results.size(), System.currentTimeMillis() - t));
    }
}
