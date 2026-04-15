package com.intelligenta.socialgraph.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for authentication operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String username;
    private String token;
    private String uid;
    
    @JsonProperty("expires_in")
    private long expiresIn;
    
    private String followers;
    private String following;
    
    @JsonProperty("activation_token")
    private String activationToken;

    public AuthResponse() {}

    public AuthResponse(String username, String token, String uid, long expiresIn) {
        this.username = username;
        this.token = token;
        this.uid = uid;
        this.expiresIn = expiresIn;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getFollowers() {
        return followers;
    }

    public void setFollowers(String followers) {
        this.followers = followers;
    }

    public String getFollowing() {
        return following;
    }

    public void setFollowing(String following) {
        this.following = following;
    }

    public String getActivationToken() {
        return activationToken;
    }

    public void setActivationToken(String activationToken) {
        this.activationToken = activationToken;
    }
}
