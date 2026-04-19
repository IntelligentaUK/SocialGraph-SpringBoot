package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.AudioSummarizer;
import com.intelligenta.socialgraph.ai.ProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.net.URI;

/**
 * Provider-agnostic {@link AudioSummarizer} backed by Spring AI's
 * {@link ChatClient}. Works for any chat-capable provider whose model accepts
 * an audio {@link Media} part (OpenAI {@code gpt-*-audio}, Vertex / Google
 * GenAI Gemini, Azure OpenAI audio). The audio clip is attached by URL so we
 * don't have to stream bytes through the JVM.
 */
public class SpringAiAudioSummarizer implements AudioSummarizer {

    private static final String SYSTEM_PROMPT = """
        You produce concise retrieval-oriented summaries from audio clips. Output
        a single paragraph, at most 256 tokens, describing speech, music, mood,
        and how the audio relates to the user's caption. Avoid first-person
        phrasing. Avoid speculation beyond what is audible.
        """;

    private static final MimeType AUDIO_MIME = MimeType.valueOf("audio/*");

    private final ChatClient chatClient;
    private final String providerKey;

    public SpringAiAudioSummarizer(ChatClient chatClient, String providerKey) {
        this.chatClient = chatClient;
        this.providerKey = providerKey;
    }

    @Override
    public String summarize(String statusText, String mediaUrl) {
        String caption = statusText == null ? "" : statusText;
        try {
            UserMessage message = UserMessage.builder()
                .text("Caption: \"" + caption + "\"\nSummarize the attached audio for retrieval.")
                .media(Media.builder()
                    .mimeType(AUDIO_MIME)
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
                "Spring AI audio summarize failed (provider=" + providerKey + ")", e);
        }
    }

    public String providerKey() {
        return providerKey;
    }
}
