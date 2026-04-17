package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.ContentModerator;
import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.ai.ImageGenerator;
import com.intelligenta.socialgraph.ai.VisualSummarizer;
import com.intelligenta.socialgraph.ai.chat.NoopSummarizer;
import com.intelligenta.socialgraph.ai.chat.SidecarSummarizer;
import com.intelligenta.socialgraph.ai.embedding.NoopEmbeddingProvider;
import com.intelligenta.socialgraph.ai.embedding.SidecarEmbeddingProvider;
import com.intelligenta.socialgraph.ai.image.NoopImageGenerator;
import com.intelligenta.socialgraph.ai.moderation.NoopModerator;
import com.intelligenta.socialgraph.service.EmbeddingClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase A wiring: exposes the sidecar-backed {@link EmbeddingProvider} and
 * {@link VisualSummarizer} as beans plus no-op image + moderation beans. Phase C
 * replaces this with per-capability {@code @ConditionalOnProperty} configs that
 * route based on {@code ai.<capability>.provider}.
 */
@Configuration
public class AiProviderWiringConfig {

    @Bean
    public EmbeddingProvider embeddingProvider(EmbeddingClient client, EmbeddingProperties props) {
        return new SidecarEmbeddingProvider(client, props);
    }

    @Bean
    public VisualSummarizer visualSummarizer(EmbeddingClient client) {
        return new SidecarSummarizer(client);
    }

    @Bean
    public ImageGenerator imageGenerator() {
        return new NoopImageGenerator();
    }

    @Bean
    public ContentModerator contentModerator() {
        return new NoopModerator();
    }

    /** Alternative no-op beans kept visible so tests can reach them if needed. */
    @SuppressWarnings("unused")
    static final Class<?> NOOP_EMBEDDING = NoopEmbeddingProvider.class;
    @SuppressWarnings("unused")
    static final Class<?> NOOP_SUMMARIZER = NoopSummarizer.class;
}
