package com.intelligenta.socialgraph.ai;

import com.intelligenta.socialgraph.ai.DefaultModelCatalog.Capability;
import com.intelligenta.socialgraph.ai.DefaultModelCatalog.Resolved;
import com.intelligenta.socialgraph.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultModelCatalogTest {

    @Test
    void audioSidecarDefaultsToGemma4Ev() {
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "sidecar", null);
        assertEquals("google/gemma-4-E4B-it", r.model());
    }

    @Test
    void videoSidecarDefaultsToGemma4Ev() {
        Resolved r = DefaultModelCatalog.resolve(Capability.VIDEO, "sidecar", null);
        assertEquals("google/gemma-4-E4B-it", r.model());
    }

    @Test
    void audioOpenAiDefaultsToWhisper() {
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "openai", null);
        assertEquals("whisper-1", r.model());
    }

    @Test
    void audioAzureOpenAiDefaultsToWhisper() {
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "azure-openai", null);
        assertEquals("whisper-1", r.model());
    }

    @Test
    void audioVertexGeminiResolvesToFlashLite() {
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "vertex-ai-gemini", null);
        assertEquals("gemini-3.1-flash-lite-preview", r.model());
    }

    @Test
    void videoVertexGeminiResolvesToFlashLite() {
        Resolved r = DefaultModelCatalog.resolve(Capability.VIDEO, "vertex-ai-gemini", null);
        assertEquals("gemini-3.1-flash-lite-preview", r.model());
    }

    @Test
    void videoOpenAiReturnsNoneBecauseNoSpringAiSupport() {
        // Spring AI 2.0.0-M4 has no provider-neutral video input for OpenAI.
        // Surfacing NONE rather than a fake default so startup can fail loudly.
        Resolved r = DefaultModelCatalog.resolve(Capability.VIDEO, "openai", null);
        assertSame(DefaultModelCatalog.NONE, r);
    }

    @Test
    void audioProviderOverrideWins() {
        AiProperties.Capability override = new AiProperties.Capability();
        override.setModel("custom-audio-model");
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "sidecar", override);
        assertEquals("custom-audio-model", r.model());
    }

    @Test
    void audioNoneProviderResolvesEmpty() {
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "none", null);
        assertEquals("", r.model());
    }

    @Test
    void audioUnknownProviderResolvesNone() {
        Resolved r = DefaultModelCatalog.resolve(Capability.AUDIO, "no-such-provider", null);
        assertSame(DefaultModelCatalog.NONE, r);
    }
}
