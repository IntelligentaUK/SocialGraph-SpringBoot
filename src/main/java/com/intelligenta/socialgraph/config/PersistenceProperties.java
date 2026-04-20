package com.intelligenta.socialgraph.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Persistence backend selection. Routes the app between the default Redis Stack
 * provider and the optional Infinispan provider.
 *
 * <pre>
 * persistence:
 *   provider: redis                  # or infinispan
 *   infinispan:
 *     client-mode: resp              # or native
 *     cluster-name: socialgraph
 *     hotrod-servers: host1:11222,host2:11222
 *     resp-host: localhost
 *     resp-port: 11222
 *     resp-password: ""
 *     ephemeral-ttl: PT24H
 *     transactional-by-default: false
 *     jgroups:
 *       stack-file: jgroups-tcp.xml
 *       initial-hosts: host1[7800],host2[7800]
 * </pre>
 *
 * <p>Every field is env-overridable via {@code PERSISTENCE_*} and
 * {@code INFINISPAN_*} environment variables wired in {@code application.yml}.
 *
 * <p>When {@code provider=redis} (default), the Infinispan block is ignored and
 * the existing {@code spring.data.redis.*} settings drive Lettuce. When
 * {@code provider=infinispan} and {@code client-mode=resp}, Lettuce is pointed
 * at the Infinispan RESP endpoint and no HotRod client is constructed. When
 * {@code client-mode=native}, the embedded {@code DefaultCacheManager} and the
 * {@code RemoteCacheManager} beans are wired and services gain access to
 * transactional caches, {@code CounterManager}, and Ickle queries.
 */
@Configuration
@ConfigurationProperties(prefix = "persistence")
public class PersistenceProperties {

    private Provider provider = Provider.REDIS;
    private Infinispan infinispan = new Infinispan();

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public Infinispan getInfinispan() { return infinispan; }
    public void setInfinispan(Infinispan infinispan) { this.infinispan = infinispan; }

    public enum Provider {
        /** Redis Stack via Lettuce (current behaviour, default). */
        REDIS,
        /** Infinispan via either the RESP endpoint or the native HotRod client. */
        INFINISPAN
    }

    public static class Infinispan {
        private ClientMode clientMode = ClientMode.RESP;
        private String clusterName = "socialgraph";
        private List<String> hotrodServers = new ArrayList<>(List.of("localhost:11222"));
        private String hotrodUsername = "";
        private String hotrodPassword = "";
        private String hotrodSaslMechanism = "DIGEST-MD5";
        private String hotrodSaslRealm = "default";
        private String respHost = "localhost";
        private int respPort = 11222;
        private String respUsername = "";
        private String respPassword = "";
        private String embeddedConfigFile = "infinispan-embedded.xml";
        private Duration ephemeralTtl = Duration.ofHours(24);
        private boolean transactionalByDefault = false;
        private JGroups jgroups = new JGroups();

        public ClientMode getClientMode() { return clientMode; }
        public void setClientMode(ClientMode clientMode) { this.clientMode = clientMode; }

        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }

        public List<String> getHotrodServers() { return hotrodServers; }
        public void setHotrodServers(List<String> hotrodServers) { this.hotrodServers = hotrodServers; }

        public String getHotrodUsername() { return hotrodUsername; }
        public void setHotrodUsername(String hotrodUsername) { this.hotrodUsername = hotrodUsername; }

        public String getHotrodPassword() { return hotrodPassword; }
        public void setHotrodPassword(String hotrodPassword) { this.hotrodPassword = hotrodPassword; }

        public String getHotrodSaslMechanism() { return hotrodSaslMechanism; }
        public void setHotrodSaslMechanism(String hotrodSaslMechanism) { this.hotrodSaslMechanism = hotrodSaslMechanism; }

        public String getHotrodSaslRealm() { return hotrodSaslRealm; }
        public void setHotrodSaslRealm(String hotrodSaslRealm) { this.hotrodSaslRealm = hotrodSaslRealm; }

        public String getRespHost() { return respHost; }
        public void setRespHost(String respHost) { this.respHost = respHost; }

        public int getRespPort() { return respPort; }
        public void setRespPort(int respPort) { this.respPort = respPort; }

        public String getRespUsername() { return respUsername; }
        public void setRespUsername(String respUsername) { this.respUsername = respUsername; }

        public String getRespPassword() { return respPassword; }
        public void setRespPassword(String respPassword) { this.respPassword = respPassword; }

        public String getEmbeddedConfigFile() { return embeddedConfigFile; }
        public void setEmbeddedConfigFile(String embeddedConfigFile) { this.embeddedConfigFile = embeddedConfigFile; }

        public Duration getEphemeralTtl() { return ephemeralTtl; }
        public void setEphemeralTtl(Duration ephemeralTtl) { this.ephemeralTtl = ephemeralTtl; }

        public boolean isTransactionalByDefault() { return transactionalByDefault; }
        public void setTransactionalByDefault(boolean transactionalByDefault) { this.transactionalByDefault = transactionalByDefault; }

        public JGroups getJgroups() { return jgroups; }
        public void setJgroups(JGroups jgroups) { this.jgroups = jgroups; }

        public enum ClientMode {
            /**
             * Drop-in Redis compatibility — existing Lettuce client talks to
             * Infinispan's RESP endpoint. No service-side refactor required.
             * Lacks RediSearch FT.* and Streams; those subsystems are wired on
             * Infinispan-native alternatives in phases I-I / I-J.
             */
            RESP,
            /**
             * Native HotRod mode — {@code RemoteCacheManager} for cluster access
             * plus an embedded {@code DefaultCacheManager} that joins the same
             * JGroups cluster for short-lived data. Unlocks transactional caches
             * (ACID via JTA), {@code CounterManager}, clustered listeners, and
             * Ickle queries.
             */
            NATIVE
        }

        public static class JGroups {
            private String stackFile = "jgroups-tcp.xml";
            private List<String> initialHosts = new ArrayList<>();

            public String getStackFile() { return stackFile; }
            public void setStackFile(String stackFile) { this.stackFile = stackFile; }

            public List<String> getInitialHosts() { return initialHosts; }
            public void setInitialHosts(List<String> initialHosts) { this.initialHosts = initialHosts; }
        }
    }
}
