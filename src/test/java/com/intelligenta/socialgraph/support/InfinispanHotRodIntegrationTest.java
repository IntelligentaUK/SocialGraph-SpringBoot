package com.intelligenta.socialgraph.support;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that exercise the Infinispan HotRod client
 * (native mode) against a single-node Infinispan 15.2 server. Activates
 * {@code persistence.provider=infinispan} with
 * {@code persistence.infinispan.client-mode=native}, wiring
 * {@link org.infinispan.client.hotrod.RemoteCacheManager},
 * {@link org.infinispan.counter.api.CounterManager}, and
 * {@link org.infinispan.manager.EmbeddedCacheManager} beans from
 * {@link com.intelligenta.socialgraph.config.InfinispanConfig}.
 *
 * <p>Uses the same {@code infinispan-resp.xml} server config as the RESP
 * profile — the endpoint exposes HotRod, RESP, and REST on a single port,
 * so one server serves both client modes. Multi-node clustering + JGroups
 * replication between the app's embedded tier and the cluster tier lands in
 * phase I-K.
 */
@Testcontainers
public abstract class InfinispanHotRodIntegrationTest {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();
    private static final String CONFIG_FILE =
        PROJECT_ROOT.resolve("docker/infinispan/infinispan-resp.xml").toString();

    @Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> INFINISPAN = new GenericContainer<>(
            DockerImageName.parse("infinispan/server:15.2"))
        .withExposedPorts(11222)
        .withEnv("USER", "admin")
        .withEnv("PASS", "admin")
        .withFileSystemBind(CONFIG_FILE,
            "/opt/infinispan/server/conf/infinispan-resp.xml",
            BindMode.READ_ONLY)
        .withCommand("-c", "infinispan-resp.xml")
        .waitingFor(Wait.forLogMessage(".*ISPN080004.*\\n", 1));

    @DynamicPropertySource
    static void registerInfinispanProperties(DynamicPropertyRegistry r) {
        r.add("persistence.provider", () -> "infinispan");
        r.add("persistence.infinispan.client-mode", () -> "native");
        r.add("persistence.infinispan.hotrod-servers",
            () -> INFINISPAN.getHost() + ":" + INFINISPAN.getMappedPort(11222));
        r.add("persistence.infinispan.hotrod-username", () -> "admin");
        r.add("persistence.infinispan.hotrod-password", () -> "admin");
        // Still wire spring.data.redis.* so Spring Boot's Lettuce auto-config
        // doesn't blow up when trying to bind its own defaults. The templates
        // themselves aren't reachable via service code in native mode once the
        // store abstractions land in I-D, but the auto-config needs valid
        // values to construct the factory at context-start time.
        r.add("spring.data.redis.host", INFINISPAN::getHost);
        r.add("spring.data.redis.port", () -> INFINISPAN.getMappedPort(11222));
        r.add("spring.data.redis.username", () -> "admin");
        r.add("spring.data.redis.password", () -> "admin");
    }
}
