package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.exception.EmbeddingSidecarException;
import com.intelligenta.socialgraph.model.embedding.EmbedImageTextRequest;
import com.intelligenta.socialgraph.model.embedding.EmbedResponse;
import com.intelligenta.socialgraph.model.embedding.EmbedTextRequest;
import com.intelligenta.socialgraph.model.embedding.SummarizeRequest;
import com.intelligenta.socialgraph.model.embedding.SummarizeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * HTTP client for the Rust embedding sidecar. Three methods, one per sidecar
 * endpoint, plus a shared timeout-configured {@code RestClient}.
 */
@Service
public class EmbeddingClient {

    private final RestClient summarizeClient;
    private final RestClient embedClient;

    @Autowired
    public EmbeddingClient(EmbeddingProperties props) {
        this(
            RestClient.builder()
                .baseUrl(props.getSidecarUrl())
                .requestFactory(factory(props.getSummarizeTimeoutMs()))
                .build(),
            RestClient.builder()
                .baseUrl(props.getSidecarUrl())
                .requestFactory(factory(props.getEmbedTimeoutMs()))
                .build()
        );
    }

    /** Test-only ctor: inject pre-configured {@code RestClient} instances. */
    EmbeddingClient(RestClient summarizeClient, RestClient embedClient) {
        this.summarizeClient = summarizeClient;
        this.embedClient = embedClient;
    }

    private static SimpleClientHttpRequestFactory factory(int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        f.setReadTimeout(timeoutMs);
        return f;
    }

    /**
     * Generate a retrieval-oriented visual summary from the caption + up to
     * {@code images_for_embedding} image URLs. Blank strings are allowed; the
     * sidecar defaults to an empty caption if needed.
     */
    public String summarize(String statusText, List<String> imageUrls) {
        try {
            SummarizeResponse resp = summarizeClient.post()
                .uri("/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SummarizeRequest(statusText, imageUrls == null ? List.of() : imageUrls))
                .retrieve()
                .body(SummarizeResponse.class);
            return resp == null ? "" : (resp.summary() == null ? "" : resp.summary());
        } catch (RestClientException e) {
            throw new EmbeddingSidecarException("summarize failed", e);
        }
    }

    /**
     * Encode both the image (at {@code imageUrl}) and the {@code textDescription}
     * with SigLIP-2 and return the L2-normalized fused vector.
     */
    public float[] embedImageAndText(String imageUrl, String textDescription) {
        try {
            EmbedResponse resp = embedClient.post()
                .uri("/embed/image-text")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedImageTextRequest(imageUrl,
                    textDescription == null ? "" : textDescription))
                .retrieve()
                .body(EmbedResponse.class);
            return toArray(resp);
        } catch (RestClientException e) {
            throw new EmbeddingSidecarException("embedImageAndText failed", e);
        }
    }

    /**
     * Encode a plain text string with SigLIP-2's text encoder and return the
     * L2-normalized 1152-dim vector.
     */
    public float[] embedText(String text) {
        try {
            EmbedResponse resp = embedClient.post()
                .uri("/embed/text")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedTextRequest(text))
                .retrieve()
                .body(EmbedResponse.class);
            return toArray(resp);
        } catch (RestClientException e) {
            throw new EmbeddingSidecarException("embedText failed", e);
        }
    }

    private static float[] toArray(EmbedResponse resp) {
        if (resp == null || resp.vector() == null) {
            throw new EmbeddingSidecarException("sidecar returned null vector");
        }
        List<Float> list = resp.vector();
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            Float f = list.get(i);
            out[i] = f == null ? 0f : f;
        }
        return out;
    }
}
