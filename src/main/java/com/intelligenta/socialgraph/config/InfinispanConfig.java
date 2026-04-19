package com.intelligenta.socialgraph.config;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.RemoteCounterManagerFactory;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.counter.api.CounterManager;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infinispan wiring. Active only when {@code persistence.provider=infinispan}.
 *
 * <p>The RESP sub-mode is handled by
 * {@link PersistenceEnvironmentPostProcessor} (it rewrites
 * {@code spring.data.redis.*} and relies on Spring Boot's Lettuce auto-config);
 * there are no additional beans to declare for RESP. The NATIVE sub-mode,
 * wired by the nested {@link Native} class, adds:
 *
 * <ul>
 *   <li>{@link RemoteCacheManager} — HotRod client for the cluster tier. All
 *   long-lived state (users, posts, reactions, timelines, counters) lands
 *   here once phases I-F through I-H wire the store implementations.</li>
 *   <li>{@link CounterManager} — derived from the remote manager so
 *   services can request strong/weak counters without handing them the
 *   underlying client.</li>
 *   <li>{@link EmbeddedCacheManager} — in-process local cache manager for
 *   the ephemeral tier (tokens, sessions, activation codes). Currently
 *   configured in LOCAL mode; the JGroups transport that lets app instances
 *   join the cluster and replicate short-lived data is added when the first
 *   ephemeral-tier store lands in phase I-E. The decoupling is deliberate:
 *   I-C proves the beans wire and one HotRod round-trip works; I-E picks
 *   the replication topology that fits the first real consumer.</li>
 * </ul>
 *
 * <p>Matches the AI-phase pattern of nested {@code @Configuration} classes
 * under a top-level {@code @ConditionalOnProperty(provider)} guard. Adding
 * a {@code client-mode=resp} nested class later is as simple as dropping
 * another static inner {@code @Configuration}.
 */
@Configuration
@ConditionalOnProperty(prefix = "persistence", name = "provider", havingValue = "infinispan")
public class InfinispanConfig {

    private static final Logger log = LoggerFactory.getLogger(InfinispanConfig.class);

    @Configuration
    @ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
    static class Native {

        @Bean(destroyMethod = "stop")
        public RemoteCacheManager remoteCacheManager(PersistenceProperties props) {
            PersistenceProperties.Infinispan ispn = props.getInfinispan();
            ConfigurationBuilder cb = new ConfigurationBuilder();
            for (String server : ispn.getHotrodServers()) {
                String host = server;
                int port = 11222;
                int colon = server.lastIndexOf(':');
                if (colon > 0) {
                    host = server.substring(0, colon);
                    port = Integer.parseInt(server.substring(colon + 1));
                }
                cb.addServer().host(host).port(port);
            }
            String user = ispn.getHotrodUsername();
            String pass = ispn.getHotrodPassword();
            if (user != null && !user.isEmpty()) {
                cb.security().authentication().enable()
                    .username(user)
                    .password(pass)
                    .saslMechanism(ispn.getHotrodSaslMechanism())
                    .realm(ispn.getHotrodSaslRealm());
            }
            RemoteCacheManager rcm = new RemoteCacheManager(cb.build());
            log.info("Infinispan HotRod client connected to {}", ispn.getHotrodServers());
            return rcm;
        }

        @Bean
        public CounterManager counterManager(RemoteCacheManager remoteCacheManager) {
            return RemoteCounterManagerFactory.asCounterManager(remoteCacheManager);
        }

        /**
         * Embedded cache manager for the ephemeral tier (short-lived data:
         * tokens, sessions, activation codes). LOCAL mode for phase I-C — no
         * JGroups transport, single-JVM caching only. Phase I-E upgrades this
         * to REPL/DIST with a JGroups transport once real services depend on
         * it, at which point app instances join the same cluster as the
         * dedicated Infinispan Server nodes and short-lived data replicates
         * out for durability.
         */
        @Bean(destroyMethod = "stop")
        public EmbeddedCacheManager embeddedCacheManager(PersistenceProperties props) {
            PersistenceProperties.Infinispan ispn = props.getInfinispan();
            GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
            global.cacheContainer().statistics(true);
            global.cacheManagerName(ispn.getClusterName() + "-embedded");

            EmbeddedCacheManager manager = new DefaultCacheManager(global.build());

            org.infinispan.configuration.cache.Configuration ephemeral =
                new org.infinispan.configuration.cache.ConfigurationBuilder()
                    .clustering().cacheMode(CacheMode.LOCAL)
                    .expiration().lifespan(ispn.getEphemeralTtl().toMillis())
                    .build();

            org.infinispan.configuration.cache.Configuration persistent =
                new org.infinispan.configuration.cache.ConfigurationBuilder()
                    .clustering().cacheMode(CacheMode.LOCAL)
                    .build();

            // Ephemeral tier
            manager.defineConfiguration("tokens", ephemeral);
            manager.defineConfiguration("sessions", ephemeral);
            manager.defineConfiguration("activations", ephemeral);

            // Cluster tier (LOCAL in phase I-D foundation; REPL/DIST when the
            // JGroups transport is wired in the next phase-refresh).
            manager.defineConfiguration("users", persistent);
            manager.defineConfiguration("user-uid-index", persistent);
            manager.defineConfiguration("relations", persistent);
            manager.defineConfiguration("content-filters", persistent);
            manager.defineConfiguration("posts", persistent);
            manager.defineConfiguration("post-replies", persistent);
            manager.defineConfiguration("post-images", persistent);
            manager.defineConfiguration("reactions", persistent);
            manager.defineConfiguration("reaction-lookups", persistent);
            manager.defineConfiguration("timelines-fifo", persistent);
            manager.defineConfiguration("timelines-personal", persistent);
            manager.defineConfiguration("timelines-everyone", persistent);
            manager.defineConfiguration("devices", persistent);
            manager.defineConfiguration("counters", persistent);

            log.info("Infinispan embedded cache manager started (LOCAL, ephemeral-ttl={})",
                ispn.getEphemeralTtl());
            return manager;
        }
    }
}
