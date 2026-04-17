package com.intelligenta.socialgraph.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligenta.socialgraph.ai.ImageGenerator;
import com.intelligenta.socialgraph.exception.GlobalExceptionHandler;
import com.intelligenta.socialgraph.model.image.GeneratedImage;
import com.intelligenta.socialgraph.model.image.ImageGenerationRequest;
import com.intelligenta.socialgraph.model.image.ImageGenerationResult;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock ImageGenerator generator;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ImageController(generator))
            .setCustomArgumentResolvers(new TestAuthenticatedUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void generateReturnsImagesFromActiveProvider() throws Exception {
        when(generator.enabled()).thenReturn(true);
        when(generator.providerKey()).thenReturn("openai");
        when(generator.modelId()).thenReturn("dall-e-3");
        when(generator.generate(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ImageGenerationResult(List.of(
                new GeneratedImage("https://cdn/x.png", null, "image/png"))));

        mockMvc.perform(post("/api/images/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ImageGenerationRequest("a neon cyberpunk cat", null, null, 1, null)))
                .with(TestRequestPostProcessors.authenticatedUser("u1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("openai"))
            .andExpect(jsonPath("$.model").value("dall-e-3"))
            .andExpect(jsonPath("$.images[0].url").value("https://cdn/x.png"));
    }

    @Test
    void generateReturns503WhenProviderDisabled() throws Exception {
        when(generator.enabled()).thenReturn(false);

        mockMvc.perform(post("/api/images/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"hello\"}")
                .with(TestRequestPostProcessors.authenticatedUser("u1")))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void generateRejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/images/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"   \"}")
                .with(TestRequestPostProcessors.authenticatedUser("u1")))
            .andExpect(status().isBadRequest());
    }
}
