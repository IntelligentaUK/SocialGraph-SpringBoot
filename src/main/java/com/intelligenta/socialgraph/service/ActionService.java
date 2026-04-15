package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.exception.PostNotFoundException;
import com.intelligenta.socialgraph.model.ActionActor;
import com.intelligenta.socialgraph.model.ActionResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for social actions (like, love, fav, share).
 */
@Service
public class ActionService {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;

    public ActionService(StringRedisTemplate redisTemplate, UserService userService) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
    }

    /**
     * List actors who performed an action on a post.
     */
    public ActionResponse listActions(Verbs.Action action, String postId, int index, int count) {
        if (!postExists(postId)) {
            throw new PostNotFoundException("Post not found");
        }

        long startTime = System.currentTimeMillis();
        
        String key = "post:" + postId + action.key();
        List<String> actorUids = redisTemplate.opsForList().range(key, index, index + count - 1);

        List<ActionActor> actors = new ArrayList<>();
        if (actorUids != null) {
            for (String uid : actorUids) {
                String username = userService.getUsername(uid);
                String fullname = userService.getUserField(uid, "fullname");
                actors.add(new ActionActor(uid, username, fullname));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new ActionResponse(action, postId, actors, actors.size(), duration);
    }

    /**
     * Perform an action on a post.
     */
    public String performAction(Verbs.Action action, String postId, String authenticatedUser) {
        if (!postExists(postId)) {
            throw new PostNotFoundException("Post not found");
        }

        if (containsAction(action, postId, authenticatedUser)) {
            return "Already " + action.pastTense() + " Post: " + postId;
        }

        String key = "post:" + postId + action.key();
        redisTemplate.opsForList().leftPush(key, authenticatedUser);
        redisTemplate.opsForHash().put(
            "post:" + postId + ":" + authenticatedUser + ":",
            action.noun(),
            "1"
        );
        return action.pastTense() + " Post: " + postId;
    }

    /**
     * Reverse an action on a post.
     */
    public String reverseAction(Verbs.Action action, String postId, String authenticatedUser) {
        if (!postExists(postId)) {
            throw new PostNotFoundException("Post not found");
        }

        if (!containsAction(action, postId, authenticatedUser)) {
            return "Cannot un" + action.noun() + ", not " + action.noun() + ": " + postId;
        }

        String key = "post:" + postId + action.key();
        redisTemplate.opsForList().remove(key, 0, authenticatedUser);
        redisTemplate.opsForHash().delete(
            "post:" + postId + ":" + authenticatedUser + ":",
            action.noun()
        );
        return "Un" + action.pastTense() + " Post: " + postId;
    }

    /**
     * Check if an action was performed by user on a post.
     */
    public boolean containsAction(Verbs.Action action, String postId, String authenticatedUser) {
        Object value = redisTemplate.opsForHash().get(
            "post:" + postId + ":" + authenticatedUser + ":", 
            action.noun()
        );
        return value != null;
    }

    private boolean postExists(String postId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("post:" + postId));
    }
}
