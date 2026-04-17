package com.intelligenta.socialgraph.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.exception.GlobalExceptionHandler;
import com.intelligenta.socialgraph.model.search.SearchRequest;
import com.intelligenta.socialgraph.model.search.SearchResult;
import com.intelligenta.socialgraph.service.VectorSearchService;
import com.intelligenta.socialgraph.support.TestAuthenticatedUserResolver;
import com.intelligenta.socialgraph.support.TestRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private VectorSearchService searchService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        EmbeddingProperties props = new EmbeddingProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService, props))
            .setCustomArgumentResolvers(new TestAuthenticatedUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void questionSearchReturnsRankedResults() throws Exception {
        when(searchService.questionSearch(eq("pier at sunset"), anyInt()))
            .thenReturn(List.of(
                new SearchResult("p1", "u1", "photo", "pier", "https://cdn/a.png",
                    List.of("https://cdn/a.png"), "1700000000", 0.12),
                new SearchResult("p2", "u2", "text", "ocean", null, null, "1700000100", 0.34)
            ));

        mockMvc.perform(post("/api/search/question")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SearchRequest("pier at sunset", 10)))
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.results[0].id").value("p1"))
            .andExpect(jsonPath("$.results[0].score").value(0.12))
            .andExpect(jsonPath("$.results[1].id").value("p2"));

        verify(searchService).questionSearch("pier at sunset", 10);
    }

    @Test
    void aiSearchUsesDefaultLimitWhenMissing() throws Exception {
        when(searchService.aiTextSearch(eq("hello"), eq(20)))
            .thenReturn(List.of());

        mockMvc.perform(post("/api/search/ai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"hello\"}")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));

        verify(searchService).aiTextSearch("hello", 20);
    }

    @Test
    void blankQueryIsRejected() throws Exception {
        mockMvc.perform(post("/api/search/question")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"   \"}")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void limitOverMaxIsRejected() throws Exception {
        mockMvc.perform(post("/api/search/ai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"hello\",\"limit\":101}")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isBadRequest());
    }
}
