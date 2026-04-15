package com.intelligenta.socialgraph.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private FilterChain filterChain;

    private TokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenAuthenticationFilter(redisTemplate);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bearerTokenCreatesAuthenticatedUserAndIncrementsPolyCount() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("tokens:good-token")).thenReturn("uid-1");
        when(hashOperations.get("user:uid", "uid-1")).thenReturn("alice");

        filter.doFilterInternal(request, response, filterChain);

        AuthenticatedUser user = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals("uid-1", user.getUid());
        assertEquals("alice", user.getUsername());
        verify(hashOperations).increment("user:alice", "polyCount", 1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingBearerTokenLeavesSecurityContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(hashOperations, never()).increment(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(filterChain).doFilter(request, response);
    }
}
