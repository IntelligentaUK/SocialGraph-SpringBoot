package com.intelligenta.socialgraph.config;

import java.util.Optional;

import com.intelligenta.socialgraph.persistence.TokenStore;
import com.intelligenta.socialgraph.persistence.UserStore;
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

    @SuppressWarnings("unchecked")
    private static final TokenStore TOKENS = Mockito.mock(TokenStore.class);
    @SuppressWarnings("unchecked")
    private static final UserStore USERS = Mockito.mock(UserStore.class);

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        Mockito.reset(TOKENS, USERS);
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
        when(TOKENS.resolve("good-token")).thenReturn(Optional.of("uid-1"));
        when(USERS.findUsernameByUid("uid-1")).thenReturn(Optional.of("alice"));

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

        when(TOKENS.resolve("good-token")).thenReturn(Optional.of("uid-1"));
        when(USERS.findUsernameByUid("uid-1")).thenReturn(Optional.of("alice"));

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
        @GetMapping("/ping") String ping() { return "public"; }
        @GetMapping("/private") String privateEndpoint(@RequestHeader(value = "Authorization", required = false) String ignored) { return "private"; }
        @GetMapping("/session") String session() { return "session"; }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean TokenAuthenticationFilter tokenAuthenticationFilter() { return new TokenAuthenticationFilter(TOKENS, USERS); }
        @Bean AppProperties appProperties() { return new AppProperties(); }
        @Bean TokenStore tokenStore() { return TOKENS; }
        @Bean UserStore userStore() { return USERS; }
    }
}
