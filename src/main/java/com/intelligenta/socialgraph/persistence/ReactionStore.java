package com.intelligenta.socialgraph.persistence;

import com.intelligenta.socialgraph.Verbs;

import java.util.List;

/**
 * Post reactions (like/love/fav/share). Redis impl preserves the "unusual"
 * {@code post:<id><verb>:} key format — {@link com.intelligenta.socialgraph.ApiSurfaceRegressionTest}
 * depends on it. Infinispan impl uses cleaner key shapes because nothing
 * external cares.
 */
public interface ReactionStore {
    void add(Verbs.Action action, String postId, String actorUid);
    void remove(Verbs.Action action, String postId, String actorUid);
    boolean contains(Verbs.Action action, String postId, String actorUid);
    List<String> listActors(Verbs.Action action, String postId, int offset, int limit);
}
