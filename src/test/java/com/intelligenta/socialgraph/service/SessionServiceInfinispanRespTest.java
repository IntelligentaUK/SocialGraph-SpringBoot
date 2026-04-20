package com.intelligenta.socialgraph.service;

import java.util.Map;

import com.intelligenta.socialgraph.SocialGraphApplication;
import com.intelligenta.socialgraph.support.InfinispanRespIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link SessionService} — a stock Redis-backed service — works
 * unchanged when {@code persistence.provider=infinispan} and
 * {@code client-mode=resp}. The Lettuce client that the service injects is
 * redirected by {@link com.intelligenta.socialgraph.config.PersistenceEnvironmentPostProcessor}
 * to the Infinispan RESP endpoint; the service code is identical in both
 * modes. If this test passes, the RESP-compat on-ramp works end-to-end for
 * the HSET / EXPIRE / EXISTS command subset.
 */
@SpringBootTest(classes = SocialGraphApplication.class)
class SessionServiceInfinispanRespTest extends InfinispanRespIntegrationTest {

    @Autowired
    SessionService sessionService;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void getSessionRoundTripsThroughInfinispanResp() {
        Map<String, Object> first = sessionService.getSession("probe-session");
        assertThat(first.get("uuid")).isEqualTo("probe-session");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) first.get("response");
        assertThat(response).containsKey("pubKey");
        assertThat((String) response.get("pubKey")).isNotBlank();

        // Session state survives via the RESP endpoint: a second call against
        // the same session id must find the key and skip regeneration.
        assertThat(redis.hasKey("session:probe-session")).isTrue();

        Map<String, Object> second = sessionService.getSession("probe-session");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondResponse = (Map<String, Object>) second.get("response");
        assertThat(secondResponse).isEmpty();
    }
}
