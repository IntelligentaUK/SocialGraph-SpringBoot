package com.intelligenta.socialgraph.persistence.infinispan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.intelligenta.socialgraph.persistence.UserStore;
import com.intelligenta.socialgraph.util.Util;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanUserStore implements UserStore {

    private final EmbeddedCacheManager manager;

    public InfinispanUserStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private Cache<String, Map<String, String>> users() {
        return (Cache<String, Map<String, String>>) (Cache<?, ?>) manager.getCache("users");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, String> uidIndex() {
        return (Cache<String, String>) (Cache<?, ?>) manager.getCache("user-uid-index");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, String> activations() {
        return (Cache<String, String>) (Cache<?, ?>) manager.getCache("activations");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, String> tokens() {
        return (Cache<String, String>) (Cache<?, ?>) manager.getCache("tokens");
    }

    @Override
    public String register(String username, Map<String, String> userHash, String uid,
                           String token, Duration tokenTtl) {
        String activationToken = Util.UUID();
        Map<String, String> stored = new LinkedHashMap<>(userHash);
        stored.put("polyCount", "1");
        users().put(username, stored);
        uidIndex().put(uid, username);
        activations().put(activationToken, uid);
        tokens().put(token, uid, tokenTtl.toMillis(), TimeUnit.MILLISECONDS);
        return activationToken;
    }

    @Override public Optional<Map<String, String>> find(String username) {
        Map<String, String> v = users().get(username);
        return v == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(v));
    }

    @Override public Optional<String> findUsernameByUid(String uid) {
        return Optional.ofNullable(uidIndex().get(uid));
    }

    @Override public Optional<String> findUidByUsername(String username) {
        Map<String, String> v = users().get(username);
        return v == null ? Optional.empty() : Optional.ofNullable(v.get("uuid"));
    }

    @Override public boolean exists(String username) { return users().containsKey(username); }
    @Override public boolean uidExists(String uid)   { return uidIndex().containsKey(uid); }

    @Override public Optional<String> getField(String username, String field) {
        Map<String, String> v = users().get(username);
        return v == null ? Optional.empty() : Optional.ofNullable(v.get(field));
    }

    @Override public List<Optional<String>> getFields(String username, List<String> fields) {
        Map<String, String> v = users().get(username);
        List<Optional<String>> out = new ArrayList<>(fields.size());
        if (v == null) {
            for (int i = 0; i < fields.size(); i++) out.add(Optional.empty());
        } else {
            for (String f : fields) out.add(Optional.ofNullable(v.get(f)));
        }
        return out;
    }

    @Override public void putField(String username, String field, String value) {
        Map<String, String> existing = users().get(username);
        Map<String, String> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        next.put(field, value);
        users().put(username, next);
    }

    @Override public void putAll(String username, Map<String, String> fields) {
        Map<String, String> existing = users().get(username);
        Map<String, String> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        next.putAll(fields);
        users().put(username, next);
    }

    @Override public void incrementField(String username, String field, long delta) {
        Map<String, String> existing = users().get(username);
        Map<String, String> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        long current = 0L;
        try { current = Long.parseLong(next.getOrDefault(field, "0")); }
        catch (NumberFormatException e) { current = 0L; }
        next.put(field, Long.toString(current + delta));
        users().put(username, next);
    }

    @Override public Map<String, String> allUidToUsername() {
        Map<String, String> out = new LinkedHashMap<>();
        uidIndex().forEach(out::put);
        return out;
    }

    @Override public Optional<String> getPublicRsaKey(String uid) {
        // Not implemented in this phase; crypto keys live out-of-band in the
        // original Redis hash `user:<uid>:crypto`. Infinispan-native storage
        // of these lands in a follow-up.
        return Optional.empty();
    }

    @Override public Optional<String> consumeActivationToken(String activationToken) {
        return Optional.ofNullable(activations().get(activationToken));
    }
}
