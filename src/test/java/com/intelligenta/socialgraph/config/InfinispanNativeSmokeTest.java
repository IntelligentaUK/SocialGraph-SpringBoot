package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.SocialGraphApplication;
import com.intelligenta.socialgraph.support.InfinispanHotRodIntegrationTest;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.counter.api.CounterManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase I-C smoke test. Proves the three native-mode beans
 * ({@link RemoteCacheManager}, {@link CounterManager}, {@link EmbeddedCacheManager})
 * wire up, the HotRod client authenticates against the test container, and
 * a simple put/get round-trips through a remote cache. No service code
 * depends on these beans yet; phases I-E through I-H wire the stores that
 * consume them.
 */
@SpringBootTest(classes = SocialGraphApplication.class)
class InfinispanNativeSmokeTest extends InfinispanHotRodIntegrationTest {

    @Autowired
    RemoteCacheManager remoteCacheManager;

    @Autowired
    CounterManager counterManager;

    @Autowired
    EmbeddedCacheManager embeddedCacheManager;

    @Test
    void nativeBeansWireUpAndHotRodRoundTripsAKey() {
        assertThat(remoteCacheManager).isNotNull();
        assertThat(counterManager).isNotNull();
        assertThat(embeddedCacheManager).isNotNull();

        RemoteCache<String, String> cache = remoteCacheManager.administration()
            .getOrCreateCache("phase-i-c-smoke",
                new org.infinispan.commons.configuration.StringConfiguration(
                    "<local-cache name=\"phase-i-c-smoke\"/>"));
        cache.put("probe", "ok");
        assertThat(cache.get("probe")).isEqualTo("ok");

        // Ephemeral-tier cache configurations are pre-defined on the embedded
        // manager (see InfinispanConfig#embeddedCacheManager). Configurations
        // are lazy-started, so we check they're defined rather than active.
        assertThat(embeddedCacheManager.getCacheConfiguration("tokens")).isNotNull();
        assertThat(embeddedCacheManager.getCacheConfiguration("sessions")).isNotNull();
        assertThat(embeddedCacheManager.getCacheConfiguration("activations")).isNotNull();
    }
}
