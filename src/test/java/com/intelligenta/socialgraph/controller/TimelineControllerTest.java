package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.model.TimelineResponse;
import com.intelligenta.socialgraph.service.TimelineService;
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
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TimelineControllerTest {

    @Mock
    private TimelineService timelineService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TimelineController(timelineService))
            .setCustomArgumentResolvers(new TestAuthenticatedUserResolver())
            .build();
    }

    @Test
    void getTimelineUsesFifoServiceMethod() throws Exception {
        when(timelineService.getFifoTimeline("viewer-1", 0, 2)).thenReturn(new TimelineResponse(List.of(), 0, 5));

        mockMvc.perform(get("/api/timeline")
                .param("index", "0")
                .param("count", "2")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));

        verify(timelineService).getFifoTimeline("viewer-1", 0, 2);
    }

    @ParameterizedTest
    @MethodSource("importanceRoutes")
    void importanceEndpointsUseMatchingImportanceMode(String path, TimelineService.Importance importance) throws Exception {
        when(timelineService.getSocialImportanceTimeline("viewer-2", 1, 3, importance))
            .thenReturn(new TimelineResponse(List.of(), 0, 6));

        mockMvc.perform(get(path)
                .param("index", "1")
                .param("count", "3")
                .with(TestRequestPostProcessors.authenticatedUser("viewer-2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duration").value(6));

        verify(timelineService).getSocialImportanceTimeline("viewer-2", 1, 3, importance);
    }

    private static Stream<Arguments> importanceRoutes() {
        return Stream.of(
            Arguments.of("/api/timeline/personal", TimelineService.Importance.PERSONAL),
            Arguments.of("/api/timeline/everyone", TimelineService.Importance.EVERYONE)
        );
    }
}
