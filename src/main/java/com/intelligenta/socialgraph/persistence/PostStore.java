package com.intelligenta.socialgraph.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Posts + replies + image URL lists. The {@link #create} contract is atomic
 * across post hash + image list + per-user content counters; Redis impl uses
 * {@code MULTI/EXEC}, Infinispan uses a transactional cache with JTA.
 */
public interface PostStore {

    /** Atomic create. Returns the post id provided. */
    String create(String postId, Map<String, String> postFields, List<String> imageUrls,
                  String authorUid, String type);

    Optional<Map<String, Object>> get(String postId);
    boolean exists(String postId);
    void update(String postId, Map<String, String> updates);
    void delete(String postId);

    void addReply(String parentPostId, String replyPostId);
    List<String> replies(String postId, int offset, int limit);

    List<String> images(String postId);
}
