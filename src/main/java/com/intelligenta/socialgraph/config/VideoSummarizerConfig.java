package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.VideoSummarizer;
import com.intelligenta.socialgraph.ai.chat.NoopVideoSummarizer;
import com.intelligenta.socialgraph.ai.chat.SidecarVideoSummarizer;
import com.intelligenta.socialgraph.service.EmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes {@code ai.video.provider} to a {@link VideoSummarizer} bean. Only
 * {@code sidecar} (default) and {@code none} are wired in this build; cloud
 * providers (Gemini) resolve correctly in
 * {@link com.intelligenta.socialgraph.ai.DefaultModelCatalog} but their
 * Spring-AI {@code ChatModel} wiring is pending a companion refactor of
 * {@link VisualSummarizerConfig} to use provider-specific {@code ChatModel}
 * classes (avoids multi-provider bean ambiguity).
 */
@Configuration
public class VideoSummarizerConfig {

    @Configuration
    @ConditionalOnProperty(
        prefix = "ai.video",
        name = "provider",
        havingValue = "sidecar",
        matchIfMissing = true
    )
    static class Sidecar {
        @Bean
        @ConditionalOnMissingBean(VideoSummarizer.class)
        public VideoSummarizer videoSummarizer(EmbeddingClient client) {
            return new SidecarVideoSummarizer(client);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.video", name = "provider", havingValue = "none")
    static class None {
        @Bean
        @ConditionalOnMissingBean(VideoSummarizer.class)
        public VideoSummarizer videoSummarizer() {
            return new NoopVideoSummarizer();
        }
    }
}
