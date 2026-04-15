package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.exception.GlobalExceptionHandler;
import com.intelligenta.socialgraph.model.MemberInfo;
import com.intelligenta.socialgraph.service.UserService;
import com.intelligenta.socialgraph.support.TestAuthenticatedUserResolver;
import com.intelligenta.socialgraph.support.TestRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new TestAuthenticatedUserResolver())
            .build();
    }

    @Test
    void followAndUnfollowReturnRelationDetails() throws Exception {
        when(userService.getUid("target-user")).thenReturn("target-uid");

        mockMvc.perform(post("/api/follow")
                .param("username", "target-user")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value("true"))
            .andExpect(jsonPath("$['following.UID']").value("target-uid"));

        mockMvc.perform(post("/api/unfollow")
                .param("uid", "target-uid")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unfollowed").value("target-uid"));

        verify(userService).follow("viewer-uid", null, "target-user");
        verify(userService).unfollow("viewer-uid", "target-uid");
    }

    @ParameterizedTest
    @MethodSource("memberRoutes")
    void memberEndpointsReturnSetTypeAndCounts(String path, String setType) throws Exception {
        when(userService.getMembers("viewer-uid", setType)).thenReturn(List.of(new MemberInfo("u1", "alice", "Alice")));

        mockMvc.perform(get(path).with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setType").value(setType))
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void profileEndpointsDelegateToUserService() throws Exception {
        when(userService.getProfile("viewer-uid", "viewer-uid")).thenReturn(Map.of("uid", "viewer-uid"));
        when(userService.updateProfile("viewer-uid", "New Name", "Bio", "pic")).thenReturn(Map.of("fullname", "New Name"));
        when(userService.getProfile("target-uid", "viewer-uid")).thenReturn(Map.of("uid", "target-uid"));
        when(userService.searchUsers("ali", 0, 2)).thenReturn(List.of(new MemberInfo("u1", "alice", "Alice")));

        mockMvc.perform(get("/api/me").with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value("viewer-uid"));

        mockMvc.perform(patch("/api/me")
                .param("fullname", "New Name")
                .param("bio", "Bio")
                .param("profilePicture", "pic")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullname").value("New Name"));

        mockMvc.perform(get("/api/users/target-uid").with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value("target-uid"));

        mockMvc.perform(get("/api/users/search").param("q", "ali").param("index", "0").param("count", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void rsaEndpointsUseRequestedOrAuthenticatedUid() throws Exception {
        when(userService.getPublicRSAKey("viewer-uid")).thenReturn("viewer-key");
        when(userService.getPublicRSAKey("target-uid")).thenReturn("target-key");

        mockMvc.perform(get("/api/me/rsa/public/key").with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(content().string("viewer-key"));

        mockMvc.perform(get("/api/rsa/public/key")
                .param("uid", "target-uid")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(content().string("target-key"));
    }

    @ParameterizedTest
    @MethodSource("relationRoutes")
    void relationMutationEndpointsReturnChangedFlag(String path, String action, boolean changed) throws Exception {
        switch (action) {
            case "blocked" -> when(userService.block("viewer-uid", "target-uid")).thenReturn(changed);
            case "unblocked" -> when(userService.unblock("viewer-uid", "target-uid")).thenReturn(changed);
            case "muted" -> when(userService.mute("viewer-uid", "target-uid")).thenReturn(changed);
            case "unmuted" -> when(userService.unmute("viewer-uid", "target-uid")).thenReturn(changed);
            default -> throw new IllegalArgumentException(action);
        }

        mockMvc.perform(post(path)
                .param("uid", "target-uid")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-uid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value("target-uid"))
            .andExpect(jsonPath("$.action").value(action))
            .andExpect(jsonPath("$.changed").value(Boolean.toString(changed)));
    }

    private static Stream<Arguments> memberRoutes() {
        return Stream.of(
            Arguments.of("/api/followers", "followers"),
            Arguments.of("/api/following", "following"),
            Arguments.of("/api/friends", "friends"),
            Arguments.of("/api/blocked", "blocked"),
            Arguments.of("/api/blockers", "blockers"),
            Arguments.of("/api/muted", "muted"),
            Arguments.of("/api/muters", "muters")
        );
    }

    private static Stream<Arguments> relationRoutes() {
        return Stream.of(
            Arguments.of("/api/block", "blocked", true),
            Arguments.of("/api/unblock", "unblocked", false),
            Arguments.of("/api/mute", "muted", true),
            Arguments.of("/api/unmute", "unmuted", false)
        );
    }
}
