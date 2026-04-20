package com.intelligenta.socialgraph.service;

import java.util.ArrayList;
import java.util.List;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.exception.PostNotFoundException;
import com.intelligenta.socialgraph.model.ActionActor;
import com.intelligenta.socialgraph.model.ActionResponse;
import com.intelligenta.socialgraph.persistence.PostStore;
import com.intelligenta.socialgraph.persistence.ReactionStore;
import org.springframework.stereotype.Service;

/** Social actions (like, love, fav, share) backed by {@link ReactionStore}. */
@Service
public class ActionService {

    private final ReactionStore reactions;
    private final PostStore posts;
    private final UserService userService;

    public ActionService(ReactionStore reactions, PostStore posts, UserService userService) {
        this.reactions = reactions;
        this.posts = posts;
        this.userService = userService;
    }

    public ActionResponse listActions(Verbs.Action action, String postId, int index, int count) {
        if (!posts.exists(postId)) throw new PostNotFoundException("Post not found");

        long startTime = System.currentTimeMillis();
        List<String> actorUids = reactions.listActors(action, postId, index, count);

        List<ActionActor> actors = new ArrayList<>();
        for (String uid : actorUids) {
            String username = userService.getUsername(uid);
            String fullname = userService.getUserField(uid, "fullname");
            actors.add(new ActionActor(uid, username, fullname));
        }
        return new ActionResponse(action, postId, actors, actors.size(),
            System.currentTimeMillis() - startTime);
    }

    public String performAction(Verbs.Action action, String postId, String authenticatedUser) {
        if (!posts.exists(postId)) throw new PostNotFoundException("Post not found");
        if (containsAction(action, postId, authenticatedUser)) {
            return "Already " + action.pastTense() + " Post: " + postId;
        }
        reactions.add(action, postId, authenticatedUser);
        return action.pastTense() + " Post: " + postId;
    }

    public String reverseAction(Verbs.Action action, String postId, String authenticatedUser) {
        if (!posts.exists(postId)) throw new PostNotFoundException("Post not found");
        if (!containsAction(action, postId, authenticatedUser)) {
            return "Cannot un" + action.noun() + ", not " + action.noun() + ": " + postId;
        }
        reactions.remove(action, postId, authenticatedUser);
        return "Un" + action.pastTense() + " Post: " + postId;
    }

    public boolean containsAction(Verbs.Action action, String postId, String authenticatedUser) {
        return reactions.contains(action, postId, authenticatedUser);
    }
}
