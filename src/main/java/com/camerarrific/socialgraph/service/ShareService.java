package com.camerarrific.socialgraph.service;

import com.camerarrific.socialgraph.dto.request.StatusRequest;
import com.camerarrific.socialgraph.model.Post;
import com.camerarrific.socialgraph.repository.PostRepository;
import com.camerarrific.socialgraph.repository.UserRepository;
import com.camerarrific.socialgraph.util.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.BreakIterator;
import java.util.*;

/**
 * Service for sharing posts/status updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShareService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    /**
     * Create a new status/post.
     */
    public Post createStatus(String authenticatedUid, StatusRequest request) {
        long startTime = System.currentTimeMillis();
        
        String postId = passwordHasher.generateUuid();
        
        Post post = Post.builder()
                .id(postId)
                .uid(authenticatedUid)
                .type(request.getType() != null ? request.getType() : "text")
                .content(request.getContent())
                .url(request.getUrl())
                .md5(request.getMd5())
                .created(String.valueOf(System.currentTimeMillis() / 1000L))
                .build();

        // Save the post
        postRepository.save(post);

        // Push to followers' timelines
        List<String> keywords = getWords(request.getContent());
        pushToGraph(authenticatedUid, postId, keywords);

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Created post {} in {}ms", postId, duration);

        return post;
    }

    /**
     * Re-share an existing post.
     */
    public Post resharePost(String authenticatedUid, String postId) {
        return postRepository.findById(postId)
                .map(originalPost -> {
                    List<String> keywords = getWords(originalPost.getContent());
                    pushToGraph(authenticatedUid, postId, keywords);
                    return originalPost;
                })
                .orElse(null);
    }

    /**
     * Push a post to all followers' timelines.
     */
    private void pushToGraph(String authorUid, String postId, List<String> keywords) {
        Set<String> followers = userRepository.getSetMembers(authorUid, "followers");
        
        for (String followerUid : followers) {
            // Check if any negative keywords match
            boolean hasNegativeKeyword = keywords.stream()
                    .anyMatch(keyword -> userRepository.hasNegativeKeyword(followerUid, keyword));
            
            if (!hasNegativeKeyword) {
                // Push to timeline
                postRepository.pushToTimeline(followerUid, postId);
                
                // Add to importance sorted sets
                // Personal importance based on connection score (simplified)
                postRepository.addToImportanceSet(followerUid, "personal", postId, 0);
                
                // Everyone importance based on social importance of author
                postRepository.addToImportanceSet(followerUid, "everyone", postId, 0);
            }
        }
        
        log.debug("Pushed post {} to {} followers", postId, followers.size());
    }

    /**
     * Extract words from text for keyword matching.
     */
    public List<String> getWords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> words = new ArrayList<>();
        BreakIterator breakIterator = BreakIterator.getWordInstance();
        breakIterator.setText(text);
        
        int lastIndex = breakIterator.first();
        while (BreakIterator.DONE != lastIndex) {
            int firstIndex = lastIndex;
            lastIndex = breakIterator.next();
            if (lastIndex != BreakIterator.DONE && 
                Character.isLetterOrDigit(text.charAt(firstIndex))) {
                String word = text.substring(firstIndex, lastIndex);
                if (!words.contains(word)) {
                    words.add(word);
                }
            }
        }
        
        return words;
    }

    /**
     * Extract hashtags from text.
     */
    public List<String> getHashTags(String text) {
        return getWords(text).stream()
                .filter(s -> s.startsWith("#"))
                .toList();
    }
}

