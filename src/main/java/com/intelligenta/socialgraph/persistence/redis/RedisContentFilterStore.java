package com.intelligenta.socialgraph.persistence.redis;

import java.util.List;

import com.intelligenta.socialgraph.persistence.ContentFilterStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisContentFilterStore implements ContentFilterStore {

    private final StringRedisTemplate redis;

    public RedisContentFilterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String keywordsKey(String uid) { return "user:" + uid + ":negative:keywords"; }
    private String imagesKey(String uid)   { return "user:" + uid + ":images:blocked:md5"; }

    @Override
    public void addNegativeKeyword(String uid, String keyword) {
        redis.opsForHash().put(keywordsKey(uid), keyword, keyword);
    }

    @Override
    public boolean hasAnyNegativeKeyword(String uid, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return false;
        for (String k : keywords) {
            if (Boolean.TRUE.equals(redis.opsForHash().hasKey(keywordsKey(uid), k))) return true;
        }
        return false;
    }

    @Override
    public void blockImage(String uid, String md5) {
        redis.opsForHash().put(imagesKey(uid), md5, md5);
    }

    @Override
    public boolean isImageBlocked(String uid, String md5) {
        if (md5 == null || md5.isBlank()) return false;
        return Boolean.TRUE.equals(redis.opsForHash().hasKey(imagesKey(uid), md5));
    }
}
