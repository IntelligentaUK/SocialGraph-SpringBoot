package com.intelligenta.socialgraph.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that validates Bearer tokens and sets up Spring Security authentication.
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate redisTemplate;

    public TokenAuthenticationFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            
            if (StringUtils.hasText(token)) {
                String uid = authenticateToken(token);
                
                if (uid != null) {
                    String username = getUsername(uid);
                    AuthenticatedUser user = new AuthenticatedUser(uid, username);
                    
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    // Increment poly count for authenticated requests
                    if (username != null) {
                        redisTemplate.opsForHash().increment("user:" + username, "polyCount", 1);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private String authenticateToken(String token) {
        return redisTemplate.opsForValue().get("tokens:" + token);
    }

    private String getUsername(String uid) {
        return (String) redisTemplate.opsForHash().get("user:uid", uid);
    }
}
