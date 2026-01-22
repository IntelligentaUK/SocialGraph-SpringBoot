package com.camerarrific.socialgraph.service;

import com.camerarrific.socialgraph.dto.response.TimelineResponse;
import com.camerarrific.socialgraph.model.Post;
import com.camerarrific.socialgraph.repository.PostRepository;
import com.camerarrific.socialgraph.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for timeline generation and retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    public enum ImportanceType {
        PERSONAL("personal"),
        EVERYONE("everyone");

        private final String value;

        ImportanceType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * Get timeline using FIFO order (most recent first).
     */
    public TimelineResponse getTimeline(String uid, int index, int count) {
        long startTime = System.currentTimeMillis();
        
        List<String> postIds = postRepository.getTimelinePosts(uid, index, count);
        List<TimelineResponse.TimelineEntity> entities = buildTimelineEntities(postIds, uid);
        
        long duration = System.currentTimeMillis() - startTime;
        
        return TimelineResponse.builder()
                .entities(entities)
                .count(entities.size())
                .duration(duration)
                .build();
    }

    /**
     * Get timeline sorted by social importance.
     */
    public TimelineResponse getTimelineByImportance(String uid, ImportanceType importanceType, int index, int count) {
        long startTime = System.currentTimeMillis();
        
        Set<String> postIds = postRepository.getTimelineByImportance(uid, importanceType.getValue(), index, count);
        List<TimelineResponse.TimelineEntity> entities = buildTimelineEntities(new ArrayList<>(postIds), uid);
        
        long duration = System.currentTimeMillis() - startTime;
        
        return TimelineResponse.builder()
                .entities(entities)
                .count(entities.size())
                .duration(duration)
                .build();
    }

    private List<TimelineResponse.TimelineEntity> buildTimelineEntities(List<String> postIds, String viewerUid) {
        List<TimelineResponse.TimelineEntity> entities = new ArrayList<>();
        
        for (String postId : postIds) {
            postRepository.findById(postId).ifPresent(post -> {
                TimelineResponse.TimelineEntity entity = buildTimelineEntity(post, viewerUid);
                if (entity != null) {
                    entities.add(entity);
                }
            });
        }
        
        return entities;
    }

    private TimelineResponse.TimelineEntity buildTimelineEntity(Post post, String viewerUid) {
        if (post.getId() == null) {
            return null;
        }

        // Build activity
        TimelineResponse.Activity activity = TimelineResponse.Activity.builder()
                .uuid(post.getId())
                .type(post.getType())
                .content(post.getContent())
                .url(post.getUrl())
                .created(post.getCreated())
                .md5(post.getMd5())
                .isLiked(postRepository.hasAction(post.getId(), viewerUid, "like"))
                .isLoved(postRepository.hasAction(post.getId(), viewerUid, "love"))
                .isFaved(postRepository.hasAction(post.getId(), viewerUid, "fav"))
                .build();

        // Build actor
        TimelineResponse.Actor actor = null;
        if (post.getUid() != null) {
            String username = userRepository.getFieldByUid(post.getUid(), "username").orElse(null);
            String fullname = userRepository.getFieldByUid(post.getUid(), "fullname").orElse(null);
            
            actor = TimelineResponse.Actor.builder()
                    .uuid(post.getUid())
                    .username(username)
                    .fullname(fullname)
                    .build();
        }

        return TimelineResponse.TimelineEntity.builder()
                .activity(activity)
                .actor(actor)
                .build();
    }
}

