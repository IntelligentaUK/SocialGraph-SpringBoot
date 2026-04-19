package com.intelligenta.socialgraph.persistence;

import java.util.Set;

/**
 * The directed-graph primitive: six logically-independent sets per user
 * (followers, following, blocked, blockers, muted, muters). Infinispan impl
 * puts these in a distributed cache; follow/unfollow cross two sets and run
 * inside a transactional cache to keep the counters honest.
 */
public interface RelationStore {
    enum Relation { FOLLOWERS, FOLLOWING, BLOCKED, BLOCKERS, MUTED, MUTERS }

    boolean add(String uid, Relation relation, String otherUid);
    boolean remove(String uid, Relation relation, String otherUid);
    boolean contains(String uid, Relation relation, String otherUid);
    Set<String> members(String uid, Relation relation);
}
