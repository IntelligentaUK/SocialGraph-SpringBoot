package com.camerarrific.socialgraph.service;

import com.camerarrific.socialgraph.dto.response.ActionResponse;
import com.camerarrific.socialgraph.exception.ApiException;
import com.camerarrific.socialgraph.model.Action;
import com.camerarrific.socialgraph.repository.PostRepository;
import com.camerarrific.socialgraph.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for social actions (like, love, fav, share).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActionService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * List actors who performed an action on a post.
     */
    public ActionResponse listActions(Action action, String postId, int index, int count) {
        long startTime = System.currentTimeMillis();
        
        if (!postRepository.exists(postId)) {
            throw new ApiException("post_not_found", "Post not found", HttpStatus.NOT_FOUND);
        }

        List<String> actorUids = postRepository.getActionActors(postId, action.getKey(), index, count);
        
        List<ActionResponse.ActionActor> actors = new ArrayList<>();
        for (String actorUid : actorUids) {
            String username = userRepository.getFieldByUid(actorUid, "username").orElse(null);
            String fullname = userRepository.getFieldByUid(actorUid, "fullname").orElse(null);
            
            actors.add(ActionResponse.ActionActor.builder()
                    .type("person")
                    .uuid(actorUid)
                    .username(username)
                    .displayName(fullname)
                    .build());
        }

        long duration = System.currentTimeMillis() - startTime;
        
        return ActionResponse.builder()
                .object(postId)
                .actors(actors)
                .count(actors.size())
                .duration(duration)
                .build();
    }

    /**
     * Perform an action on a post.
     */
    public Map<String, String> performAction(Action action, String postId, String actorUid) {
        if (!postRepository.exists(postId)) {
            throw new ApiException("post_not_found", "Post not found", HttpStatus.NOT_FOUND);
        }

        Map<String, String> result = new HashMap<>();
        
        // Add action to list
        Long added = postRepository.addAction(postId, action.getKey(), actorUid);
        
        if (added != null && added == 1) {
            // First time - set the flag
            postRepository.setActionFlag(postId, actorUid, action.getNoun());
            result.put(action.getPastTense() + "Post", postId);
        } else {
            result.put("already" + capitalize(action.getPastTense()) + "Post", postId);
        }

        log.debug("User {} {} post {}", actorUid, action.getPastTense(), postId);
        return result;
    }

    /**
     * Reverse an action on a post (unlike, unlove, etc.).
     */
    public Map<String, String> reverseAction(Action action, String postId, String actorUid) {
        if (!postRepository.exists(postId)) {
            throw new ApiException("post_not_found", "Post not found", HttpStatus.NOT_FOUND);
        }

        Map<String, String> result = new HashMap<>();
        
        // Remove action from list
        Long removed = postRepository.removeAction(postId, action.getKey(), actorUid);
        
        if (removed != null && removed == 1) {
            // Successfully removed - clear the flag
            postRepository.removeActionFlag(postId, actorUid, action.getNoun());
            result.put("un" + action.getPastTense() + "Post", postId);
        } else {
            result.put("cannotUn" + action.getNoun(), "not " + action.getPastTense());
        }

        log.debug("User {} un{} post {}", actorUid, action.getPastTense(), postId);
        return result;
    }

    /**
     * Check if a user has performed an action on a post.
     */
    public boolean hasAction(Action action, String postId, String actorUid) {
        return postRepository.hasAction(postId, actorUid, action.getNoun());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

