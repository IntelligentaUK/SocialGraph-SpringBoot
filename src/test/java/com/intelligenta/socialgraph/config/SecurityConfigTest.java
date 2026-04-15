package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.security.TokenAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = SecurityConfigTest.TestApplication.class,
    properties = "spring.data.redis.repositories.enabled=false"
)
class SecurityConfigTest {

    private static final StringRedisTemplate REDIS_TEMPLATE = Mockito.mock(StringRedisTemplate.class);
    private static final ValueOperations<String, String> VALUE_OPERATIONS = Mockito.mock(ValueOperations.class);
    private static final HashOperations<String, Object, Object> HASH_OPERATIONS = Mockito.mock(HashOperations.class);

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        Mockito.reset(REDIS_TEMPLATE, VALUE_OPERATIONS, HASH_OPERATIONS);
        when(REDIS_TEMPLATE.opsForValue()).thenReturn(VALUE_OPERATIONS);
        when(REDIS_TEMPLATE.opsForHash()).thenReturn(HASH_OPERATIONS);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void publicEndpointsAreAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/ping"))
            .andExpect(status().isOk())
            .andExpect(content().string("public"));

        mockMvc.perform(get("/api/session"))
            .andExpect(status().isOk())
            .andExpect(content().string("session"));
    }

    @Test
    void protectedEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/private"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void protectedEndpointAcceptsValidBearerToken() throws Exception {
        when(VALUE_OPERATIONS.get("tokens:good-token")).thenReturn("uid-1");
        when(HASH_OPERATIONS.get("user:uid", "uid-1")).thenReturn("alice");

        mockMvc.perform(get("/api/private")
                .header("Authorization", "Bearer good-token")
                .accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(content().string("private"));
    }

    @Test
    void legacyAdvancedAuthEndpointIsNotPublicOrImplemented() throws Exception {
        mockMvc.perform(get("/api/aes/key"))
            .andExpect(status().isForbidden());

        when(VALUE_OPERATIONS.get("tokens:good-token")).thenReturn("uid-1");
        when(HASH_OPERATIONS.get("user:uid", "uid-1")).thenReturn("alice");

        mockMvc.perform(get("/api/aes/key")
                .header("Authorization", "Bearer good-token")
                .accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isNotFound());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, ProbeController.class, TestBeans.class})
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/api")
    static class ProbeController {

        @GetMapping("/ping")
        String ping() {
            return "public";
        }

        @GetMapping("/private")
        String privateEndpoint(@RequestHeader(value = "Authorization", required = false) String ignored) {
            return "private";
        }

        @GetMapping("/session")
        String session() {
            return "session";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        TokenAuthenticationFilter tokenAuthenticationFilter() {
            return new TokenAuthenticationFilter(redisTemplate());
        }

        @Bean
        AppProperties appProperties() {
            return new AppProperties();
        }

        @Bean
        StringRedisTemplate redisTemplate() {
            return REDIS_TEMPLATE;
        }
    }
}
