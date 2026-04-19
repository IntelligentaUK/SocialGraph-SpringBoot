package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.persistence.ReactionStore;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanReactionStore implements ReactionStore {

    private final EmbeddedCacheManager manager;

    public InfinispanReactionStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private Cache<String, ArrayList<String>> list() {
        return (Cache<String, ArrayList<String>>) (Cache<?, ?>) manager.getCache("reactions");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, Map<String, HashSet<String>>> lookup() {
        return (Cache<String, Map<String, HashSet<String>>>)
            (Cache<?, ?>) manager.getCache("reaction-lookups");
    }

    private String listKey(String postId, Verbs.Action action) {
        return postId + ":" + action.noun();
    }

    @Override public void add(Verbs.Action action, String postId, String actorUid) {
        ArrayList<String> actors = list().getOrDefault(listKey(postId, action), new ArrayList<>());
        if (actors.contains(actorUid)) return;
        ArrayList<String> next = new ArrayList<>(actors);
        next.add(0, actorUid);
        list().put(listKey(postId, action), next);

        Map<String, HashSet<String>> perPost = lookup().getOrDefault(postId, new LinkedHashMap<>());
        Map<String, HashSet<String>> nextPost = new LinkedHashMap<>(perPost);
        HashSet<String> verbsForActor = new HashSet<>(nextPost.getOrDefault(actorUid, new HashSet<>()));
        verbsForActor.add(action.noun());
        nextPost.put(actorUid, verbsForActor);
        lookup().put(postId, nextPost);
    }

    @Override public void remove(Verbs.Action action, String postId, String actorUid) {
        ArrayList<String> actors = list().get(listKey(postId, action));
        if (actors != null && actors.contains(actorUid)) {
            ArrayList<String> next = new ArrayList<>(actors);
            next.remove(actorUid);
            list().put(listKey(postId, action), next);
        }
        Map<String, HashSet<String>> perPost = lookup().get(postId);
        if (perPost != null && perPost.containsKey(actorUid)) {
            Map<String, HashSet<String>> nextPost = new LinkedHashMap<>(perPost);
            HashSet<String> verbsForActor = new HashSet<>(nextPost.getOrDefault(actorUid, new HashSet<>()));
            verbsForActor.remove(action.noun());
            if (verbsForActor.isEmpty()) {
                nextPost.remove(actorUid);
            } else {
                nextPost.put(actorUid, verbsForActor);
            }
            lookup().put(postId, nextPost);
        }
    }

    @Override public boolean contains(Verbs.Action action, String postId, String actorUid) {
        Map<String, HashSet<String>> perPost = lookup().get(postId);
        if (perPost == null) return false;
        HashSet<String> verbs = perPost.get(actorUid);
        return verbs != null && verbs.contains(action.noun());
    }

    @Override public List<String> listActors(Verbs.Action action, String postId, int offset, int limit) {
        ArrayList<String> actors = list().get(listKey(postId, action));
        if (actors == null || actors.isEmpty()) return Collections.emptyList();
        int from = Math.max(0, offset);
        int to = Math.min(actors.size(), offset + limit);
        return from >= actors.size() ? Collections.emptyList() : new ArrayList<>(actors.subList(from, to));
    }
}
