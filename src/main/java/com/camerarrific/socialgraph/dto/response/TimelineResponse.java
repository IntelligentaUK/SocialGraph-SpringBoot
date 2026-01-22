package com.camerarrific.socialgraph.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for timeline data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Timeline response")
public class TimelineResponse {

    @Schema(description = "List of timeline entities/posts")
    private List<TimelineEntity> entities;

    @Schema(description = "Total count of returned entities", example = "20")
    private Integer count;

    @Schema(description = "Request duration in milliseconds", example = "45")
    private Long duration;

    /**
     * Individual timeline entity (post with actor info).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Timeline entity")
    public static class TimelineEntity {

        @Schema(description = "Activity/post details")
        private Activity activity;

        @Schema(description = "Actor/author details")
        private Actor actor;
    }

    /**
     * Activity/post details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Activity/post details")
    public static class Activity {

        @Schema(description = "Post UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String uuid;

        @Schema(description = "Content type (text, photo, video)", example = "photo")
        private String type;

        @Schema(description = "Post content/caption", example = "Beautiful sunset!")
        private String content;

        @Schema(description = "Media URL", example = "https://storage.example.com/photo.jpg")
        private String url;

        @Schema(description = "Creation timestamp (Unix epoch)", example = "1706000000")
        private String created;

        @Schema(description = "MD5 hash of media", example = "d41d8cd98f00b204e9800998ecf8427e")
        private String md5;

        @Schema(description = "Whether current user liked this post")
        private Boolean isLiked;

        @Schema(description = "Whether current user loved this post")
        private Boolean isLoved;

        @Schema(description = "Whether current user favorited this post")
        private Boolean isFaved;
    }

    /**
     * Actor/author details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Actor/author details")
    public static class Actor {

        @Schema(description = "Actor UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String uuid;

        @Schema(description = "Actor username", example = "johndoe")
        private String username;

        @Schema(description = "Actor full name", example = "John Doe")
        private String fullname;
    }
}

