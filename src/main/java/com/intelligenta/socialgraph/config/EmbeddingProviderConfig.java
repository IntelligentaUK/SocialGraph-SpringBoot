package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.DefaultModelCatalog;
import com.intelligenta.socialgraph.ai.DefaultModelCatalog.Capability;
import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.ai.embedding.NoopEmbeddingProvider;
import com.intelligenta.socialgraph.ai.embedding.SidecarEmbeddingProvider;
import com.intelligenta.socialgraph.ai.embedding.SpringAiEmbeddingProvider;
import com.intelligenta.socialgraph.service.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes {@code ai.embedding.provider} to an {@link EmbeddingProvider} bean.
 * Each provider is its own nested {@link Configuration}; exactly one is
 * activated by {@link ConditionalOnProperty}. Provider-specific configs import
 * only their own Spring AI autoconfig class so the long
 * {@code spring.autoconfigure.exclude} list in {@code application.yml} stays
 * in effect for every provider that isn't selected.
 */
@Configuration
public class EmbeddingProviderConfig {

    // ---------- Sidecar (default) ----------

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "sidecar", matchIfMissing = true)
    static class Sidecar {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingClient client, EmbeddingProperties props) {
            return new SidecarEmbeddingProvider(client, props);
        }
    }

    // ---------- None ----------

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "none")
    static class None {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider() {
            return new NoopEmbeddingProvider();
        }
    }

    // ---------- Spring AI providers ----------

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "openai")
    @ImportAutoConfiguration(org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class)
    static class OpenAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "openai", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "openai", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "azure-openai")
    @ImportAutoConfiguration(org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingAutoConfiguration.class)
    static class AzureOpenAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "azure-openai", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "azure-openai", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "ollama")
    @ImportAutoConfiguration({
        org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
        org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class
    })
    static class Ollama {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "ollama", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "ollama", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "vertex-ai-embedding")
    @ImportAutoConfiguration({
        org.springframework.ai.model.vertexai.autoconfigure.embedding.VertexAiEmbeddingConnectionAutoConfiguration.class,
        org.springframework.ai.model.vertexai.autoconfigure.embedding.VertexAiTextEmbeddingAutoConfiguration.class
    })
    static class VertexAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "vertex-ai-embedding", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "vertex-ai-embedding", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "google-genai-embedding")
    @ImportAutoConfiguration({
        org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
        org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration.class
    })
    static class GoogleGenAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "google-genai-embedding", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "google-genai-embedding", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "mistral-ai")
    @ImportAutoConfiguration(org.springframework.ai.model.mistralai.autoconfigure.MistralAiEmbeddingAutoConfiguration.class)
    static class MistralAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "mistral-ai", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "mistral-ai", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "zhipuai")
    @ImportAutoConfiguration(org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiEmbeddingAutoConfiguration.class)
    static class ZhiPuAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "zhipuai", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "zhipuai", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "minimax")
    @ImportAutoConfiguration(org.springframework.ai.model.minimax.autoconfigure.MiniMaxEmbeddingAutoConfiguration.class)
    static class MiniMax {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "minimax", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "minimax", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "oci-genai")
    @ImportAutoConfiguration({
        org.springframework.ai.model.oci.genai.autoconfigure.OCIGenAiInferenceClientAutoConfiguration.class,
        org.springframework.ai.model.oci.genai.autoconfigure.OCIGenAiEmbeddingAutoConfiguration.class
    })
    static class OciGenAi {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "oci-genai", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "oci-genai", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "bedrock-titan")
    @ImportAutoConfiguration(org.springframework.ai.model.bedrock.titan.autoconfigure.BedrockTitanEmbeddingAutoConfiguration.class)
    static class BedrockTitan {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "bedrock", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "bedrock-titan", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "bedrock-cohere")
    @ImportAutoConfiguration(org.springframework.ai.model.bedrock.cohere.autoconfigure.BedrockCohereEmbeddingAutoConfiguration.class)
    static class BedrockCohere {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "bedrock", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "bedrock-cohere", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "postgresml")
    @ImportAutoConfiguration(org.springframework.ai.model.postgresml.autoconfigure.PostgresMlEmbeddingAutoConfiguration.class)
    static class PostgresMl {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "postgresml-embedding", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "postgresml", r.dimensions());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "transformers")
    @ImportAutoConfiguration(org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration.class)
    static class Transformers {
        @Bean
        @ConditionalOnMissingBean(EmbeddingProvider.class)
        public EmbeddingProvider embeddingProvider(EmbeddingModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.EMBEDDING, "transformers", props.getEmbedding());
            return new SpringAiEmbeddingProvider(model, "transformers", r.dimensions());
        }
    }
}
