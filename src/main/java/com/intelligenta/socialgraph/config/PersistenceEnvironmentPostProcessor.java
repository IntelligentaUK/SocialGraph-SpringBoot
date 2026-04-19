package com.intelligenta.socialgraph.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Rewrites {@code spring.data.redis.host/port/username/password} to point at
 * the Infinispan RESP endpoint when {@code persistence.provider=infinispan}
 * and {@code persistence.infinispan.client-mode=resp}. This lets the existing
 * Lettuce auto-configuration construct a {@code LettuceConnectionFactory}
 * targeting Infinispan's RESP endpoint without service code changes and
 * without us having to re-implement Spring Data Redis's pool / SSL / cluster
 * setup.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} so the overrides are visible
 * to every downstream {@code @ConfigurationProperties} binder, including the
 * Spring Boot-managed {@code RedisProperties}. Registered via
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 *
 * <p>When {@code provider=redis} (default) or {@code client-mode=native}, this
 * post-processor is a no-op: the existing {@code spring.data.redis.*} values
 * win and native mode wires its own beans in {@link InfinispanNativeConfig}
 * (phase I-C).
 */
public class PersistenceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PersistenceEnvironmentPostProcessor.class);

    private static final String SOURCE_NAME = "persistence-infinispan-resp-overrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String provider = env.getProperty("persistence.provider", "redis");
        String mode = env.getProperty("persistence.infinispan.client-mode", "resp");

        if (!"infinispan".equalsIgnoreCase(provider) || !"resp".equalsIgnoreCase(mode)) {
            return;
        }

        String host = env.getProperty("persistence.infinispan.resp-host", "localhost");
        String port = env.getProperty("persistence.infinispan.resp-port", "11222");
        String username = env.getProperty("persistence.infinispan.resp-username", "");
        String password = env.getProperty("persistence.infinispan.resp-password", "");

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.data.redis.host", host);
        overrides.put("spring.data.redis.port", port);
        if (!username.isEmpty()) {
            overrides.put("spring.data.redis.username", username);
        }
        if (!password.isEmpty()) {
            overrides.put("spring.data.redis.password", password);
        }

        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, overrides));
        log.info("Persistence: redirecting spring.data.redis -> Infinispan RESP endpoint at {}:{}", host, port);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
