package com.intelligenta.socialgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-specific configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private List<String> publicEndpoints = new ArrayList<>();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }

    public void setPublicEndpoints(List<String> publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    public static class Security {
        private long tokenExpirationSeconds = 86400;

        public long getTokenExpirationSeconds() {
            return tokenExpirationSeconds;
        }

        public void setTokenExpirationSeconds(long tokenExpirationSeconds) {
            this.tokenExpirationSeconds = tokenExpirationSeconds;
        }
    }
}
