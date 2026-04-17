package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.ContentModerator;
import com.intelligenta.socialgraph.ai.moderation.NoopModerator;
import com.intelligenta.socialgraph.ai.moderation.SpringAiModerator;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Routes {@code ai.moderation.provider} to a {@link ContentModerator} bean. */
@Configuration
public class ContentModeratorConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "ai.moderation", name = "provider", havingValue = "none", matchIfMissing = true)
    static class None {
        @Bean
        @ConditionalOnMissingBean(ContentModerator.class)
        public ContentModerator contentModerator() {
            return new NoopModerator();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.moderation", name = "provider", havingValue = "openai")
    @ImportAutoConfiguration(org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class)
    static class OpenAi {
        @Bean
        @ConditionalOnMissingBean(ContentModerator.class)
        public ContentModerator contentModerator(ModerationModel model) {
            return new SpringAiModerator(model, "openai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.moderation", name = "provider", havingValue = "mistral-ai")
    @ImportAutoConfiguration(org.springframework.ai.model.mistralai.autoconfigure.MistralAiModerationAutoConfiguration.class)
    static class MistralAi {
        @Bean
        @ConditionalOnMissingBean(ContentModerator.class)
        public ContentModerator contentModerator(ModerationModel model) {
            return new SpringAiModerator(model, "mistral-ai");
        }
    }
}
