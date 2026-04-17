package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.VisualSummarizer;
import com.intelligenta.socialgraph.ai.chat.NoopSummarizer;
import com.intelligenta.socialgraph.ai.chat.SidecarSummarizer;
import com.intelligenta.socialgraph.ai.chat.SpringAiChatSummarizer;
import com.intelligenta.socialgraph.service.EmbeddingClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes {@code ai.chat.provider} to a {@link VisualSummarizer} bean. Spring
 * AI's {@link ChatClient} is provider-neutral; we build one from whichever
 * {@link ChatModel} the selected provider's autoconfig produced.
 */
@Configuration
public class VisualSummarizerConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "sidecar", matchIfMissing = true)
    static class Sidecar {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(EmbeddingClient client) {
            return new SidecarSummarizer(client);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "none")
    static class None {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer() {
            return new NoopSummarizer();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "openai")
    @ImportAutoConfiguration(org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class)
    static class OpenAi {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "openai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "azure-openai")
    @ImportAutoConfiguration(org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration.class)
    static class AzureOpenAi {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "azure-openai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "anthropic")
    @ImportAutoConfiguration(org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration.class)
    static class Anthropic {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "anthropic");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "google-genai")
    @ImportAutoConfiguration(org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration.class)
    static class GoogleGenAi {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "google-genai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "vertex-ai-gemini")
    @ImportAutoConfiguration(org.springframework.ai.model.vertexai.autoconfigure.gemini.VertexAiGeminiChatAutoConfiguration.class)
    static class VertexGemini {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "vertex-ai-gemini");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "bedrock-converse")
    @ImportAutoConfiguration(org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration.class)
    static class BedrockConverse {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "bedrock-converse");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "ollama")
    @ImportAutoConfiguration({
        org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
        org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration.class
    })
    static class Ollama {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "ollama");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "mistral-ai")
    @ImportAutoConfiguration(org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration.class)
    static class MistralAi {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "mistral-ai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "zhipuai")
    @ImportAutoConfiguration(org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiChatAutoConfiguration.class)
    static class ZhiPuAi {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "zhipuai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "minimax")
    @ImportAutoConfiguration(org.springframework.ai.model.minimax.autoconfigure.MiniMaxChatAutoConfiguration.class)
    static class MiniMax {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "minimax");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "oci-genai")
    @ImportAutoConfiguration({
        org.springframework.ai.model.oci.genai.autoconfigure.OCIGenAiInferenceClientAutoConfiguration.class,
        org.springframework.ai.model.oci.genai.autoconfigure.OCIGenAiChatAutoConfiguration.class
    })
    static class OciGenAi {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "oci-genai");
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "deepseek")
    @ImportAutoConfiguration(org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration.class)
    static class DeepSeek {
        @Bean
        @ConditionalOnMissingBean(VisualSummarizer.class)
        public VisualSummarizer visualSummarizer(ChatModel model) {
            return new SpringAiChatSummarizer(ChatClient.builder(model).build(), "deepseek");
        }
    }
}
