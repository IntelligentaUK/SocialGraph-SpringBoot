package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.ProviderException;
import com.intelligenta.socialgraph.ai.VideoSummarizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.net.URI;

/**
 * Provider-agnostic {@link VideoSummarizer} backed by Spring AI's
 * {@link ChatClient}. Works for any chat-capable provider whose model accepts
 * a video {@link Media} part (Vertex / Google GenAI Gemini natively, plus any
 * future OpenAI-family video-input model). The clip is attached by URL.
 */
public class SpringAiVideoSummarizer implements VideoSummarizer {

    private static final String SYSTEM_PROMPT = """
        You produce concise retrieval-oriented summaries from video clips. Output
        a single paragraph, at most 256 tokens, describing scenes, actions, spoken
        content, and how the video relates to the user's caption. Avoid
        first-person phrasing. Avoid speculation beyond what is visible or
        audible.
        """;

    private static final MimeType VIDEO_MIME = MimeType.valueOf("video/*");

    private final ChatClient chatClient;
    private final String providerKey;

    public SpringAiVideoSummarizer(ChatClient chatClient, String providerKey) {
        this.chatClient = chatClient;
        this.providerKey = providerKey;
    }

    @Override
    public String summarize(String statusText, String mediaUrl) {
        String caption = statusText == null ? "" : statusText;
        try {
            UserMessage message = UserMessage.builder()
                .text("Caption: \"" + caption + "\"\nSummarize the attached video for retrieval.")
                .media(Media.builder()
                    .mimeType(VIDEO_MIME)
                    .data(URI.create(mediaUrl))
                    .build())
                .build();

            String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(message)
                .call()
                .content();
            return content == null ? "" : content.trim();
        } catch (RuntimeException e) {
            throw new ProviderException(
                "Spring AI video summarize failed (provider=" + providerKey + ")", e);
        }
    }

    public String providerKey() {
        return providerKey;
    }
}
