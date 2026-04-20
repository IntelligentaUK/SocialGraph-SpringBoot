package com.intelligenta.socialgraph.persistence.redis;

import java.util.Collections;
import java.util.List;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.persistence.ReactionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisReactionStore implements ReactionStore {

    private final StringRedisTemplate redis;

    public RedisReactionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // "Unusual" key shape preserved for ApiSurfaceRegressionTest / schema parity:
    //   post:<postId><suffix>   where suffix is "likes:" / "loves:" / "favs:" / "shares:"
    private static String listKey(String postId, Verbs.Action action) {
        return "post:" + postId + action.key();
    }

    private static String lookupKey(String postId, String actorUid) {
        return "post:" + postId + ":" + actorUid + ":";
    }

    @Override
    public void add(Verbs.Action action, String postId, String actorUid) {
        redis.opsForList().leftPush(listKey(postId, action), actorUid);
        redis.opsForHash().put(lookupKey(postId, actorUid), action.noun(), "1");
    }

    @Override
    public void remove(Verbs.Action action, String postId, String actorUid) {
        redis.opsForList().remove(listKey(postId, action), 0, actorUid);
        redis.opsForHash().delete(lookupKey(postId, actorUid), action.noun());
    }

    @Override
    public boolean contains(Verbs.Action action, String postId, String actorUid) {
        return redis.opsForHash().get(lookupKey(postId, actorUid), action.noun()) != null;
    }

    @Override
    public List<String> listActors(Verbs.Action action, String postId, int offset, int limit) {
        List<String> out = redis.opsForList().range(listKey(postId, action), offset, offset + limit - 1);
        return out == null ? Collections.emptyList() : out;
    }
}
