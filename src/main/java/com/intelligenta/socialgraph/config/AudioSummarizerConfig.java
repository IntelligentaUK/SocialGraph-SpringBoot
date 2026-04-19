package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.AudioSummarizer;
import com.intelligenta.socialgraph.ai.chat.NoopAudioSummarizer;
import com.intelligenta.socialgraph.ai.chat.SidecarAudioSummarizer;
import com.intelligenta.socialgraph.service.EmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes {@code ai.audio.provider} to an {@link AudioSummarizer} bean. Only
 * {@code sidecar} (default) and {@code none} are wired in this build; cloud
 * providers (OpenAI Whisper / Azure / Gemini) resolve correctly in
 * {@link com.intelligenta.socialgraph.ai.DefaultModelCatalog} but their
 * Spring-AI {@code ChatModel} wiring is pending a companion refactor of
 * {@link VisualSummarizerConfig} to use provider-specific {@code ChatModel}
 * classes (avoids multi-provider bean ambiguity).
 */
@Configuration
public class AudioSummarizerConfig {

    @Configuration
    @ConditionalOnProperty(
        prefix = "ai.audio",
        name = "provider",
        havingValue = "sidecar",
        matchIfMissing = true
    )
    static class Sidecar {
        @Bean
        @ConditionalOnMissingBean(AudioSummarizer.class)
        public AudioSummarizer audioSummarizer(EmbeddingClient client) {
            return new SidecarAudioSummarizer(client);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.audio", name = "provider", havingValue = "none")
    static class None {
        @Bean
        @ConditionalOnMissingBean(AudioSummarizer.class)
        public AudioSummarizer audioSummarizer() {
            return new NoopAudioSummarizer();
        }
    }
}
