package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.model.TimelineEntry;
import com.intelligenta.socialgraph.model.TimelineResponse;
import com.intelligenta.socialgraph.service.ShareService;
import com.intelligenta.socialgraph.service.TimelineService;
import com.intelligenta.socialgraph.support.TestAuthenticatedUserResolver;
import com.intelligenta.socialgraph.support.TestRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock
    private ShareService shareService;

    @Mock
    private TimelineService timelineService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        mockMvc = MockMvcBuilders.standaloneSetup(new StatusController(shareService, timelineService, redisTemplate))
            .setCustomArgumentResolvers(new TestAuthenticatedUserResolver())
            .build();
    }

    @Test
    void postStatusRoutesByTypeAndValidatesRequiredFields() throws Exception {
        when(shareService.shareText("viewer-uid", "hello")).thenReturn(Map.of("type", "text"));
        when(shareService.sharePhoto("viewer-uid", "caption", "https://cdn.example/photo.jpg"))
            .thenReturn(Map.of("type", "photo"));
        when(shareService.shareVideo("viewer-uid", "caption", "https://cdn.example/video.mp4"))
            .thenReturn(Map.of("type", "video"));

        mockMvc.perform(post("/api/status")
                .param("type", "text")
                .param("content", "hello")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("text"));

        mockMvc.perform(post("/api/status")
                .param("type", "photo")
                .param("content", "caption")
                .param("url", "https://cdn.example/photo.jpg")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("photo"));

        mockMvc.perform(post("/api/status")
                .param("type", "video")
                .param("content", "caption")
                .param("url", "https://cdn.example/video.mp4")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("video"));

        mockMvc.perform(post("/api/status")
                .param("type", "text")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postAndReplyEndpointsDelegateToServices() throws Exception {
        TimelineEntry entry = new TimelineEntry();
        entry.setUuid("post-1");
        when(timelineService.getPost("viewer-uid", "post-1")).thenReturn(entry);
        when(timelineService.getReplies("viewer-uid", "post-1", 0, 5)).thenReturn(new TimelineResponse(java.util.List.of(entry), 1, 3));
        when(shareService.replyToPost("viewer-uid", "post-1", "reply")).thenReturn(Map.of("id", "reply-1"));
        when(shareService.resharePost("viewer-uid", "post-1", "note")).thenReturn(Map.of("id", "reshare-1"));
        when(shareService.updatePost("viewer-uid", "post-1", "edited")).thenReturn(Map.of("id", "post-1"));
        when(shareService.deletePost("viewer-uid", "post-1")).thenReturn(Map.of("deleted", "true"));

        mockMvc.perform(get("/api/posts/post-1").with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value("post-1"));

        mockMvc.perform(get("/api/posts/post-1/replies")
                .param("index", "0")
                .param("count", "5")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/posts/post-1/reply")
                .param("content", "reply")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("reply-1"));

        mockMvc.perform(post("/api/posts/post-1/reshare")
                .param("content", "note")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("reshare-1"));

        mockMvc.perform(patch("/api/posts/post-1")
                .param("content", "edited")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("post-1"));

        mockMvc.perform(delete("/api/posts/post-1").with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value("true"));
    }

    @Test
    void deviceAndFilterEndpointsUseRedisState() throws Exception {
        when(setOperations.members("user:viewer-uid:devices")).thenReturn(Set.of("device-1", "device-2"));
        when(hashOperations.putIfAbsent("user:viewer-uid:negative:keywords", "spoiler", "spoiler")).thenReturn(true);
        when(shareService.getPost("post-1")).thenReturn(Map.of("imageHash", "abc123"));
        when(hashOperations.putIfAbsent("user:viewer-uid:images:blocked:md5", "abc123", "abc123")).thenReturn(false);

        mockMvc.perform(get("/api/devices/registered")
                .param("index", "0")
                .param("count", "10")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(post("/api/add/keyword/negative")
                .param("keyword", "spoiler")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.added").value("true"));

        mockMvc.perform(post("/api/add/image/block")
                .param("postId", "post-1")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageHash").value("abc123"))
            .andExpect(jsonPath("$.added").value("false"));

        mockMvc.perform(post("/api/add/image/block").with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isBadRequest());
    }
}
