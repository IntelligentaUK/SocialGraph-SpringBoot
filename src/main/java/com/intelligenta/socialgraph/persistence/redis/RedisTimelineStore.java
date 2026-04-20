package com.intelligenta.socialgraph.persistence.redis;

import java.util.Collections;
import java.util.List;

import com.intelligenta.socialgraph.persistence.TimelineStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisTimelineStore implements TimelineStore {

    private final StringRedisTemplate redis;

    public RedisTimelineStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String fifoKey(String uid)     { return "user:" + uid + ":timeline"; }
    private static String personalKey(String uid) { return "user:" + uid + ":timeline:personal:importance"; }
    private static String everyoneKey(String uid) { return "user:" + uid + ":timeline:everyone:importance"; }

    @Override
    public void push(String uid, String postId, double fifoTimestamp, double personalScore, double everyoneScore) {
        redis.opsForList().leftPush(fifoKey(uid), postId);
        redis.opsForZSet().add(personalKey(uid), postId, personalScore);
        redis.opsForZSet().add(everyoneKey(uid), postId, everyoneScore);
    }

    @Override
    public void pushMany(List<String> recipientUids, String postId,
                         double fifoTimestamp, double personalScore, double everyoneScore) {
        for (String uid : recipientUids) {
            push(uid, postId, fifoTimestamp, personalScore, everyoneScore);
        }
    }

    @Override
    public List<String> range(String uid, Kind kind, int offset, int limit) {
        return switch (kind) {
            case FIFO -> {
                List<String> out = redis.opsForList().range(fifoKey(uid), offset, offset + limit - 1);
                yield out == null ? Collections.emptyList() : out;
            }
            case PERSONAL_IMPORTANCE -> {
                var s = redis.opsForZSet().reverseRange(personalKey(uid), offset, offset + limit - 1);
                yield s == null ? Collections.emptyList() : List.copyOf(s);
            }
            case EVERYONE_IMPORTANCE -> {
                var s = redis.opsForZSet().reverseRange(everyoneKey(uid), offset, offset + limit - 1);
                yield s == null ? Collections.emptyList() : List.copyOf(s);
            }
        };
    }
}
