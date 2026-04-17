package com.intelligenta.socialgraph.ai.image;

import com.intelligenta.socialgraph.ai.ImageGenerator;
import com.intelligenta.socialgraph.ai.ProviderException;
import com.intelligenta.socialgraph.model.image.ImageGenerationRequest;
import com.intelligenta.socialgraph.model.image.ImageGenerationResult;

/**
 * Default when {@code ai.image.provider=none}. {@link #enabled()} returns
 * false, which the controller surfaces as {@code 503 service_unavailable}.
 */
public class NoopImageGenerator implements ImageGenerator {

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        throw new ProviderException("image generation is disabled (ai.image.provider=none)");
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String modelId() {
        return "";
    }

    @Override
    public String providerKey() {
        return "none";
    }
}
