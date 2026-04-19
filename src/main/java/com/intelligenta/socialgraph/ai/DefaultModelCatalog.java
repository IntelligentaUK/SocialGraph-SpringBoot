package com.intelligenta.socialgraph.ai;

import com.intelligenta.socialgraph.config.AiProperties;

/**
 * Curated default-model table for every (capability, provider) pair supported
 * by Spring AI 2.0.0-M4 + the sidecar. The app ships with these defaults so a
 * bare {@code ai.embedding.provider=openai} Just Works without the admin
 * having to pick a model. {@code ai.<capability>.model} / {@code dimensions}
 * on {@link AiProperties.Capability} override per-install.
 *
 * <p>Unsupported (capability, provider) combinations return {@link #NONE}
 * which callers translate into a startup error — e.g. picking
 * {@code ai.image.provider=anthropic} is meaningless (Anthropic has no
 * image-gen API) and we'd rather fail loudly than silently fall back.
 */
public final class DefaultModelCatalog {

    private DefaultModelCatalog() {}

    public enum Capability { EMBEDDING, CHAT, IMAGE, MODERATION, AUDIO, VIDEO }

    public record Resolved(String model, int dimensions) {}

    /** Sentinel for unsupported (capability, provider) pairs. */
    public static final Resolved NONE = new Resolved(null, 0);

    /**
     * Resolve the active model + dimension for this capability on this
     * provider, applying any per-capability {@code model}/{@code dimensions}
     * override from {@link AiProperties.Capability}.
     */
    public static Resolved resolve(Capability cap, String provider, AiProperties.Capability override) {
        Resolved base = baseDefault(cap, provider);
        if (base == NONE) return NONE;
        String model = override != null && override.getModel() != null && !override.getModel().isBlank()
            ? override.getModel() : base.model();
        int dim = override != null && override.getDimensions() != null
            ? override.getDimensions() : base.dimensions();
        return new Resolved(model, dim);
    }

    private static Resolved baseDefault(Capability cap, String provider) {
        String p = provider == null ? "" : provider.toLowerCase();
        return switch (cap) {
            case EMBEDDING -> embeddingDefault(p);
            case CHAT      -> chatDefault(p);
            case IMAGE     -> imageDefault(p);
            case MODERATION -> moderationDefault(p);
            case AUDIO     -> audioDefault(p);
            case VIDEO     -> videoDefault(p);
        };
    }

    private static Resolved embeddingDefault(String p) {
        return switch (p) {
            case "openai"                   -> new Resolved("text-embedding-3-small", 1536);
            case "azure-openai"             -> new Resolved("text-embedding-3-small", 1536);
            case "vertex-ai-embedding",
                 "google-genai-embedding"   -> new Resolved("text-embedding-005", 768);
            case "bedrock", "bedrock-converse" -> new Resolved("amazon.titan-embed-text-v2:0", 1024);
            case "ollama"                   -> new Resolved("nomic-embed-text", 768);
            case "mistral-ai"               -> new Resolved("mistral-embed", 1024);
            case "zhipuai"                  -> new Resolved("embedding-3", 2048);
            case "minimax"                  -> new Resolved("embo-01", 1536);
            case "oci-genai"                -> new Resolved("cohere.embed-multilingual-v3.0", 1024);
            case "postgresml-embedding"     -> new Resolved("intfloat/e5-small", 384);
            case "transformers"             -> new Resolved("sentence-transformers/all-MiniLM-L6-v2", 384);
            case "sidecar"                  -> new Resolved("google/siglip2-giant-opt-patch16-384", 1152);
            case "none"                     -> new Resolved("", 0);
            default                         -> NONE;
        };
    }

    private static Resolved chatDefault(String p) {
        // Dimensions is unused for chat; carry 0.
        return switch (p) {
            case "openai"                   -> new Resolved("gpt-5.4-mini-2026-03-17", 0);
            case "azure-openai"             -> new Resolved("gpt-5.4-mini-2026-03-17", 0);
            case "anthropic"                -> new Resolved("claude-opus-4-7", 0);
            case "google-genai",
                 "vertex-ai-gemini"         -> new Resolved("gemini-3.1-flash-lite-preview", 0);
            case "bedrock", "bedrock-converse" -> new Resolved("anthropic.claude-opus-4-7", 0);
            case "ollama"                   -> new Resolved("gemma4:e2b", 0);
            case "mistral-ai"               -> new Resolved("mistral-large-latest", 0);
            case "zhipuai"                  -> new Resolved("glm-4-plus", 0);
            case "minimax"                  -> new Resolved("abab6.5-chat", 0);
            case "oci-genai"                -> new Resolved("cohere.command-r-plus", 0);
            case "deepseek"                 -> new Resolved("deepseek-chat", 0);
            case "sidecar"                  -> new Resolved("google/gemma-4-31B-it", 0);
            case "none"                     -> new Resolved("", 0);
            default                         -> NONE;
        };
    }

    private static Resolved imageDefault(String p) {
        return switch (p) {
            case "openai"                   -> new Resolved("dall-e-3", 0);
            case "azure-openai"             -> new Resolved("dall-e-3", 0);
            case "stability-ai"             -> new Resolved("stable-diffusion-xl-1024-v1-0", 0);
            case "zhipuai"                  -> new Resolved("cogview-3", 0);
            case "none"                     -> new Resolved("", 0);
            default                         -> NONE;
        };
    }

    private static Resolved moderationDefault(String p) {
        return switch (p) {
            case "openai"                   -> new Resolved("omni-moderation-latest", 0);
            case "mistral-ai"               -> new Resolved("mistral-moderation-latest", 0);
            case "none"                     -> new Resolved("", 0);
            default                         -> NONE;
        };
    }

    private static Resolved audioDefault(String p) {
        // Dimensions is unused for audio summaries; carry 0.
        return switch (p) {
            case "sidecar"                  -> new Resolved("google/gemma-4-E4B-it", 0);
            case "openai"                   -> new Resolved("whisper-1", 0);
            case "azure-openai"             -> new Resolved("whisper-1", 0);
            case "google-genai",
                 "vertex-ai-gemini"         -> new Resolved("gemini-3.1-flash-lite-preview", 0);
            case "none"                     -> new Resolved("", 0);
            default                         -> NONE;
        };
    }

    private static Resolved videoDefault(String p) {
        // Dimensions is unused for video summaries; carry 0.
        return switch (p) {
            case "sidecar"                  -> new Resolved("google/gemma-4-E4B-it", 0);
            case "google-genai",
                 "vertex-ai-gemini"         -> new Resolved("gemini-3.1-flash-lite-preview", 0);
            case "none"                     -> new Resolved("", 0);
            default                         -> NONE;
        };
    }

    /**
     * True when the active provider can accept images in chat messages (for
     * the visual summary path).
     */
    public static boolean chatSupportsVision(String provider) {
        String p = provider == null ? "" : provider.toLowerCase();
        return switch (p) {
            case "openai", "azure-openai", "anthropic",
                 "google-genai", "vertex-ai-gemini",
                 "bedrock", "bedrock-converse", "ollama",
                 "sidecar" -> true;
            default -> false;
        };
    }

    /**
     * True when the active embedding provider has a genuine image+text shared
     * space (Vertex AI multimodalembedding or the Rust sidecar). Everyone else
     * falls back to embedding the Gemma summary as text.
     */
    public static boolean embeddingSupportsMultimodal(String provider) {
        String p = provider == null ? "" : provider.toLowerCase();
        return "sidecar".equals(p) || "vertex-ai-multimodal".equals(p);
    }
}
