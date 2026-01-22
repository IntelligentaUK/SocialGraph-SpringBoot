package com.camerarrific.socialgraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain model representing a post/status update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post implements Serializable {

    private String id;
    private String uid;
    private String type;
    private String content;
    private String url;
    private String md5;
    private String created;
    private Long likes;
    private Long loves;
    private Long favs;
    private Long shares;

    /**
     * Post types.
     */
    public enum Type {
        TEXT("text"),
        PHOTO("photo"),
        VIDEO("video");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Type fromString(String value) {
            for (Type type : Type.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return TEXT;
        }
    }

    /**
     * Create a Post from a Redis hash map.
     */
    public static Post fromMap(Map<String, String> map) {
        return Post.builder()
                .id(map.get("id"))
                .uid(map.get("uid"))
                .type(map.get("type"))
                .content(map.get("content"))
                .url(map.get("url"))
                .md5(map.get("md5"))
                .created(map.get("created"))
                .likes(parseLong(map.get("likes")))
                .loves(parseLong(map.get("loves")))
                .favs(parseLong(map.get("favs")))
                .shares(parseLong(map.get("shares")))
                .build();
    }

    /**
     * Convert to a Redis hash map.
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        if (id != null) map.put("id", id);
        if (uid != null) map.put("uid", uid);
        if (type != null) map.put("type", type);
        if (content != null) map.put("content", content);
        if (url != null) map.put("url", url);
        if (md5 != null) map.put("md5", md5);
        if (created != null) map.put("created", created);
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
}

