package com.camerarrific.socialgraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Domain model representing a user in the social graph.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private String uid;
    private String username;
    private String email;
    private String fullname;
    private String passwordHash;
    private String salt;
    private String poly;
    private Long followers;
    private Long following;
    private Long polyCount;
    private String profilePicture;
    private Boolean activated;
    private Instant createdAt;
    private Instant lastLoginAt;

    /**
     * Create a User from a Redis hash map.
     */
    public static User fromMap(java.util.Map<String, String> map) {
        return User.builder()
                .uid(map.get("uuid"))
                .username(map.get("username"))
                .email(map.get("email"))
                .fullname(map.get("fullname"))
                .passwordHash(map.get("passwordHash"))
                .salt(map.get("salt"))
                .poly(map.get("poly"))
                .followers(parseLong(map.get("followers")))
                .following(parseLong(map.get("following")))
                .polyCount(parseLong(map.get("polyCount")))
                .profilePicture(map.get("profilePicture"))
                .activated(parseBoolean(map.get("activated")))
                .build();
    }

    /**
     * Convert to a Redis hash map.
     */
    public java.util.Map<String, String> toMap() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (uid != null) map.put("uuid", uid);
        if (email != null) map.put("email", email);
        if (fullname != null) map.put("fullname", fullname);
        if (passwordHash != null) map.put("passwordHash", passwordHash);
        if (salt != null) map.put("salt", salt);
        if (poly != null) map.put("poly", poly);
        if (followers != null) map.put("followers", String.valueOf(followers));
        if (following != null) map.put("following", String.valueOf(following));
        if (polyCount != null) map.put("polyCount", String.valueOf(polyCount));
        if (profilePicture != null) map.put("profilePicture", profilePicture);
        if (activated != null) map.put("activated", String.valueOf(activated));
        return map;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value);
    }
}

