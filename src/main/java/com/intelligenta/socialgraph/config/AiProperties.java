package com.intelligenta.socialgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Per-capability AI provider configuration. Each capability independently
 * selects a provider from the 18 Spring AI starters bundled + {@code sidecar}
 * + {@code none} and optionally overrides the model / dimensions / generation
 * params without leaving YAML.
 *
 * <pre>
 * ai:
 *   embedding:   { provider: openai, model: text-embedding-3-large, dimensions: 1024 }
 *   chat:        { provider: anthropic, temperature: 0.2 }
 *   image:       { provider: stability-ai }
 *   moderation:  { provider: openai }
 * </pre>
 *
 * Anything under {@code spring.ai.<provider>.*} flows through unchanged —
 * that's the Spring AI namespace for API keys, base URLs, Azure deployment
 * names, Vertex project IDs, etc.
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Capability embedding = new Capability();
    private Capability chat = new Capability();
    private Capability image = new Capability();
    private Capability moderation = new Capability();

    public Capability getEmbedding() { return embedding; }
    public void setEmbedding(Capability embedding) { this.embedding = embedding; }

    public Capability getChat() { return chat; }
    public void setChat(Capability chat) { this.chat = chat; }

    public Capability getImage() { return image; }
    public void setImage(Capability image) { this.image = image; }

    public Capability getModeration() { return moderation; }
    public void setModeration(Capability moderation) { this.moderation = moderation; }

    public static class Capability {
        /**
         * Provider key — one of {@code sidecar}, {@code none}, or a Spring AI
         * provider identifier ({@code openai}, {@code azure-openai}, etc.).
         */
        private String provider = "sidecar";

        /**
         * Model identifier; when null the {@code DefaultModelCatalog} fills in
         * the provider's recommended default for this capability.
         */
        private String model;

        /** Embeddings only. Null → provider default. */
        private Integer dimensions;

        /** Chat / image generation. Null → provider default. */
        private Double temperature;

        /** Chat only. Null → provider default. */
        private Integer maxTokens;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public Integer getDimensions() { return dimensions; }
        public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }

        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }

        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    }
}
