package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.exception.EmbeddingSidecarException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddingClientTest {

    private MockRestServiceServer summarizeServer;
    private MockRestServiceServer embedServer;
    private EmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder summarizeBuilder = RestClient.builder().baseUrl("http://localhost:8000");
        RestClient.Builder embedBuilder = RestClient.builder().baseUrl("http://localhost:8000");
        summarizeServer = MockRestServiceServer.bindTo(summarizeBuilder).build();
        embedServer = MockRestServiceServer.bindTo(embedBuilder).build();
        client = new EmbeddingClient(summarizeBuilder.build(), embedBuilder.build());
    }

    @Test
    void summarizeSendsStatusTextAndImageUrlsAndParsesResponse() {
        summarizeServer.expect(requestTo("http://localhost:8000/summarize"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.status_text").value("sunset"))
            .andExpect(jsonPath("$.image_urls[0]").value("https://cdn/a.png"))
            .andExpect(jsonPath("$.image_urls[1]").value("https://cdn/b.png"))
            .andRespond(withSuccess("{\"summary\":\"a sunset over water\"}", MediaType.APPLICATION_JSON));

        String summary = client.summarize("sunset", List.of("https://cdn/a.png", "https://cdn/b.png"));

        assertEquals("a sunset over water", summary);
        summarizeServer.verify();
    }

    @Test
    void embedTextSendsRequestAndReturnsFloats() {
        embedServer.expect(requestTo("http://localhost:8000/embed/text"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.text").value("hello"))
            .andRespond(withSuccess("{\"vector\":[0.1,0.2,0.3]}", MediaType.APPLICATION_JSON));

        float[] v = client.embedText("hello");

        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, v, 1e-6f);
        embedServer.verify();
    }

    @Test
    void embedImageAndTextSendsCorrectBodyAndParsesResponse() {
        embedServer.expect(requestTo("http://localhost:8000/embed/image-text"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.image_url").value("https://cdn/x.png"))
            .andExpect(jsonPath("$.text_description").value("a cat"))
            .andRespond(withSuccess("{\"vector\":[1.0,0.0]}", MediaType.APPLICATION_JSON));

        float[] v = client.embedImageAndText("https://cdn/x.png", "a cat");

        assertArrayEquals(new float[]{1.0f, 0.0f}, v, 1e-6f);
        embedServer.verify();
    }

    @Test
    void nonSuccessSidecarResponseSurfacesAsEmbeddingSidecarException() {
        embedServer.expect(requestTo("http://localhost:8000/embed/text"))
            .andRespond(withServerError());

        EmbeddingSidecarException ex = assertThrows(EmbeddingSidecarException.class,
            () -> client.embedText("boom"));
        assertEquals("embedText failed", ex.getMessage());
    }

    @Test
    void summarizeAudioSendsStatusTextAndMediaUrlAndParsesResponse() {
        summarizeServer.expect(requestTo("http://localhost:8000/summarize/audio"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.status_text").value("my podcast"))
            .andExpect(jsonPath("$.media_url").value("https://cdn/clip.mp3"))
            .andRespond(withSuccess("{\"summary\":\"an interview about AI\"}",
                MediaType.APPLICATION_JSON));

        String summary = client.summarizeAudio("my podcast", "https://cdn/clip.mp3");

        assertEquals("an interview about AI", summary);
        summarizeServer.verify();
    }

    @Test
    void summarizeVideoSendsStatusTextAndMediaUrlAndParsesResponse() {
        summarizeServer.expect(requestTo("http://localhost:8000/summarize/video"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.status_text").value("dog at beach"))
            .andExpect(jsonPath("$.media_url").value("https://cdn/clip.mp4"))
            .andRespond(withSuccess("{\"summary\":\"a dog running on sand\"}",
                MediaType.APPLICATION_JSON));

        String summary = client.summarizeVideo("dog at beach", "https://cdn/clip.mp4");

        assertEquals("a dog running on sand", summary);
        summarizeServer.verify();
    }

    @Test
    void summarizeAudioNonSuccessSurfacesAsEmbeddingSidecarException() {
        summarizeServer.expect(requestTo("http://localhost:8000/summarize/audio"))
            .andRespond(withServerError());

        EmbeddingSidecarException ex = assertThrows(EmbeddingSidecarException.class,
            () -> client.summarizeAudio("x", "https://cdn/clip.mp3"));
        assertEquals("summarizeAudio failed", ex.getMessage());
    }
}
