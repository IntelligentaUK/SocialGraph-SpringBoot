package com.intelligenta.socialgraph.ai.image;

import com.intelligenta.socialgraph.ai.ImageGenerator;
import com.intelligenta.socialgraph.ai.ProviderException;
import com.intelligenta.socialgraph.model.image.GeneratedImage;
import com.intelligenta.socialgraph.model.image.ImageGenerationRequest;
import com.intelligenta.socialgraph.model.image.ImageGenerationResult;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-agnostic {@link ImageGenerator} backed by Spring AI's
 * {@link ImageModel}. Works for OpenAI DALL-E, Azure OpenAI, Stability AI,
 * Zhipu cogview — every provider in the BOM that implements {@code ImageModel}.
 */
public class SpringAiImageGenerator implements ImageGenerator {

    private final ImageModel model;
    private final String providerKey;
    private final String modelId;

    public SpringAiImageGenerator(ImageModel model, String providerKey, String modelId) {
        this.model = model;
        this.providerKey = providerKey;
        this.modelId = modelId;
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        try {
            ImagePrompt prompt = new ImagePrompt(request.prompt());
            ImageResponse response = model.call(prompt);
            List<GeneratedImage> out = new ArrayList<>();
            if (response != null && response.getResults() != null) {
                response.getResults().forEach(result -> {
                    Image img = result.getOutput();
                    if (img == null) return;
                    String url = img.getUrl();
                    String b64 = img.getB64Json();
                    if (url != null || b64 != null) {
                        out.add(new GeneratedImage(url, b64, b64 != null ? "image/png" : null));
                    }
                });
            }
            return new ImageGenerationResult(out);
        } catch (RuntimeException e) {
            throw new ProviderException(
                "Spring AI image generation failed (provider=" + providerKey + ")", e);
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public String providerKey() {
        return providerKey;
    }
}
