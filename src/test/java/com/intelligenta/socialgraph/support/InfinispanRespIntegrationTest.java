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
 * Base class for integration tests that run against Infinispan's RESP
 * endpoint instead of Redis Stack. Spins up a single-node Infinispan 15.2
 * server container per test class, mounts
 * {@code docker/infinispan/infinispan-resp.xml}, and registers the runtime
 * properties that switch the app into Infinispan RESP mode
 * ({@code persistence.provider=infinispan},
 * {@code persistence.infinispan.client-mode=resp}).
 *
 * <p>The {@link com.intelligenta.socialgraph.config.PersistenceEnvironmentPostProcessor}
 * handles redirecting {@code spring.data.redis.*} to the container's mapped
 * RESP port, so no service code is aware of this switch. Tests that inherit
 * from this class exercise real services against the Infinispan RESP
 * endpoint, proving the drop-in compatibility.
 *
 * <p>Vector search and the embedding queue are <em>not</em> wired in this
 * mode (their beans are conditional on {@code persistence.provider=redis});
 * phases I-I and I-J restore them on Infinispan-native primitives.
 */
@Testcontainers
public abstract class InfinispanRespIntegrationTest {

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
        r.add("persistence.infinispan.client-mode", () -> "resp");
        r.add("persistence.infinispan.resp-host", INFINISPAN::getHost);
        r.add("persistence.infinispan.resp-port", () -> INFINISPAN.getMappedPort(11222));
        r.add("persistence.infinispan.resp-username", () -> "admin");
        r.add("persistence.infinispan.resp-password", () -> "admin");
        // The Spring Boot Lettuce auto-config binds to spring.data.redis.*; the
        // EnvironmentPostProcessor re-binds them from persistence.infinispan.*
        // at application startup, but @DynamicPropertySource runs after that
        // cycle. Mirror the values directly so the ConnectionFactory picks up
        // the container's mapped port.
        r.add("spring.data.redis.host", INFINISPAN::getHost);
        r.add("spring.data.redis.port", () -> INFINISPAN.getMappedPort(11222));
        r.add("spring.data.redis.username", () -> "admin");
        r.add("spring.data.redis.password", () -> "admin");
    }
}
