package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.model.ActionResponse;
import com.intelligenta.socialgraph.service.ActionService;
import com.intelligenta.socialgraph.support.TestAuthenticatedUserResolver;
import com.intelligenta.socialgraph.support.TestRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActionControllerTest {

    @Mock
    private ActionService actionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActionController(actionService))
            .setCustomArgumentResolvers(new TestAuthenticatedUserResolver())
            .build();
    }

    @ParameterizedTest
    @MethodSource("listRoutes")
    void actionListEndpointsUseExpectedAction(String path, Verbs.Action action) throws Exception {
        when(actionService.listActions(action, "post-1", 0, 2))
            .thenReturn(new ActionResponse(action, "post-1", List.of(), 0, 9));

        mockMvc.perform(get(path)
                .param("uuid", "post-1")
                .param("index", "0")
                .param("count", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionType").value(action.plural()));

        verify(actionService).listActions(action, "post-1", 0, 2);
    }

    @ParameterizedTest
    @MethodSource("mutationRoutes")
    void mutationEndpointsRouteToCorrectServiceMethod(String path, Verbs.Action action, boolean reverse, String result) throws Exception {
        if (reverse) {
            when(actionService.reverseAction(action, "post-2", "user-1")).thenReturn(result);
        } else {
            when(actionService.performAction(action, "post-2", "user-1")).thenReturn(result);
        }

        mockMvc.perform(post(path)
                .param("uuid", "post-2")
                .with(TestRequestPostProcessors.authenticatedUser("user-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value(result));

        if (reverse) {
            verify(actionService).reverseAction(action, "post-2", "user-1");
        } else {
            verify(actionService).performAction(action, "post-2", "user-1");
        }
    }

    private static Stream<Arguments> listRoutes() {
        return Stream.of(
            Arguments.of("/api/likes", Verbs.Action.LIKE),
            Arguments.of("/api/loves", Verbs.Action.LOVE),
            Arguments.of("/api/faves", Verbs.Action.FAV),
            Arguments.of("/api/shares", Verbs.Action.SHARE)
        );
    }

    private static Stream<Arguments> mutationRoutes() {
        return Stream.of(
            Arguments.of("/api/like", Verbs.Action.LIKE, false, "liked"),
            Arguments.of("/api/unlike", Verbs.Action.LIKE, true, "unliked"),
            Arguments.of("/api/love", Verbs.Action.LOVE, false, "loved"),
            Arguments.of("/api/unlove", Verbs.Action.LOVE, true, "unloved"),
            Arguments.of("/api/fav", Verbs.Action.FAV, false, "faved"),
            Arguments.of("/api/unfav", Verbs.Action.FAV, true, "unfaved"),
            Arguments.of("/api/share", Verbs.Action.SHARE, false, "shared"),
            Arguments.of("/api/unshare", Verbs.Action.SHARE, true, "unshared")
        );
    }
}
