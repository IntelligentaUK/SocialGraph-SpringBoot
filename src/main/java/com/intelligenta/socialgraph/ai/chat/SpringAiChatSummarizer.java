package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.DefaultModelCatalog;
import com.intelligenta.socialgraph.ai.ProviderException;
import com.intelligenta.socialgraph.ai.VisualSummarizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.List;

/**
 * Provider-agnostic {@link VisualSummarizer} backed by Spring AI's
 * {@link ChatClient}. The same class works for every chat-capable provider —
 * OpenAI, Anthropic, Vertex/Gemini, Bedrock, Ollama, ... — because
 * {@code ChatClient} is the provider-neutral entry point.
 *
 * <p>When the active provider supports vision the user message includes each
 * image URL as a {@link Media} part; when not, image URLs are dropped and the
 * caption is paraphrased from text alone.
 */
public class SpringAiChatSummarizer implements VisualSummarizer {

    private static final String SYSTEM_PROMPT = """
        You produce concise visual summaries for retrieval. Output a single
        paragraph, at most 256 tokens, describing the scene, objects, setting,
        mood, and how they relate to the user's caption. Avoid first-person
        phrasing. Avoid speculation beyond what is visible.
        """;

    private final ChatClient chatClient;
    private final String providerKey;
    private final boolean supportsVision;

    public SpringAiChatSummarizer(ChatClient chatClient, String providerKey) {
        this.chatClient = chatClient;
        this.providerKey = providerKey;
        this.supportsVision = DefaultModelCatalog.chatSupportsVision(providerKey);
    }

    @Override
    public String summarize(String statusText, List<String> imageUrls) {
        String caption = statusText == null ? "" : statusText;
        try {
            UserMessage.Builder userBuilder = UserMessage.builder()
                .text("Caption: \"" + caption + "\"\n" +
                    (supportsVision && imageUrls != null && !imageUrls.isEmpty()
                        ? "Images follow. Write the summary."
                        : "Write the summary based on the caption."));

            if (supportsVision && imageUrls != null) {
                for (String url : imageUrls) {
                    userBuilder.media(Media.builder()
                        .mimeType(MimeTypeUtils.IMAGE_JPEG)
                        .data(URI.create(url))
                        .build());
                }
            }

            String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(userBuilder.build())
                .call()
                .content();
            return content == null ? "" : content.trim();
        } catch (RuntimeException e) {
            throw new ProviderException(
                "Spring AI chat summarize failed (provider=" + providerKey + ")", e);
        }
    }

    @Override
    public boolean supportsVision() {
        return supportsVision;
    }

    public String providerKey() {
        return providerKey;
    }
}
