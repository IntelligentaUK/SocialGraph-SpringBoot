package com.intelligenta.socialgraph.persistence.infinispan;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.intelligenta.socialgraph.persistence.TokenStore;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanTokenStore implements TokenStore {

    private final EmbeddedCacheManager manager;

    public InfinispanTokenStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @Override public void issue(String token, String uid, Duration ttl) {
        manager.<String, String>getCache("tokens").put(token, uid, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override public Optional<String> resolve(String token) {
        return Optional.ofNullable(manager.<String, String>getCache("tokens").get(token));
    }

    @Override public void revoke(String token) {
        manager.<String, String>getCache("tokens").remove(token);
    }
}
