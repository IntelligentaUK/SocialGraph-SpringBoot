package com.intelligenta.socialgraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligenta.socialgraph.controller.ActionController;
import com.intelligenta.socialgraph.controller.AuthController;
import com.intelligenta.socialgraph.controller.ImageController;
import com.intelligenta.socialgraph.controller.SearchController;
import com.intelligenta.socialgraph.controller.StatusController;
import com.intelligenta.socialgraph.controller.StorageController;
import com.intelligenta.socialgraph.controller.TimelineController;
import com.intelligenta.socialgraph.controller.UserController;
import com.intelligenta.socialgraph.model.ActionActor;
import com.intelligenta.socialgraph.model.ActionResponse;
import com.intelligenta.socialgraph.model.MemberInfo;
import com.intelligenta.socialgraph.model.MembersResponse;
import com.intelligenta.socialgraph.model.TimelineEntry;
import com.intelligenta.socialgraph.model.TimelineResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSurfaceRegressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void migratedApiRetainsLegacySocialRoutesAndOmitsLegacyCryptoHelpers() {
        Set<Route> routes = collectRoutes(
            ActionController.class,
            AuthController.class,
            ImageController.class,
            SearchController.class,
            StatusController.class,
            StorageController.class,
            TimelineController.class,
            UserController.class
        );

        Set<Route> legacySocialRoutes = Set.of(
            route("GET", "/api/activate"),
            route("GET", "/api/blockers"),
            route("GET", "/api/blocked"),
            route("GET", "/api/devices/registered"),
            route("GET", "/api/faves"),
            route("GET", "/api/followers"),
            route("GET", "/api/following"),
            route("GET", "/api/friends"),
            route("GET", "/api/likes"),
            route("GET", "/api/loves"),
            route("GET", "/api/me/rsa/public/key"),
            route("GET", "/api/muted"),
            route("GET", "/api/muters"),
            route("GET", "/api/ping"),
            route("GET", "/api/rsa/public/key"),
            route("GET", "/api/session"),
            route("GET", "/api/shares"),
            route("GET", "/api/timeline"),
            route("POST", "/api/add/image/block"),
            route("POST", "/api/add/keyword/negative"),
            route("POST", "/api/fav"),
            route("POST", "/api/follow"),
            route("POST", "/api/login"),
            route("POST", "/api/love"),
            route("POST", "/api/like"),
            route("POST", "/api/lq/upload"),
            route("POST", "/api/register"),
            route("POST", "/api/request/storage/key"),
            route("POST", "/api/share"),
            route("POST", "/api/status"),
            route("POST", "/api/unfav"),
            route("POST", "/api/unfollow"),
            route("POST", "/api/unlove"),
            route("POST", "/api/unlike"),
            route("POST", "/api/unshare")
        );

        Set<Route> missingLegacyRoutes = new LinkedHashSet<>(legacySocialRoutes);
        missingLegacyRoutes.removeAll(routes);
        assertTrue(missingLegacyRoutes.isEmpty(), () -> "Missing migrated legacy routes: " + missingLegacyRoutes);

        assertTrue(routes.contains(route("GET", "/api/timeline/personal")));
        assertTrue(routes.contains(route("GET", "/api/timeline/everyone")));
        assertTrue(routes.contains(route("GET", "/api/me")));
        assertTrue(routes.contains(route("PATCH", "/api/me")));
        assertTrue(routes.contains(route("GET", "/api/users/{uid}")));
        assertTrue(routes.contains(route("GET", "/api/users/search")));
        assertTrue(routes.contains(route("POST", "/api/block")));
        assertTrue(routes.contains(route("POST", "/api/unblock")));
        assertTrue(routes.contains(route("POST", "/api/mute")));
        assertTrue(routes.contains(route("POST", "/api/unmute")));
        assertTrue(routes.contains(route("GET", "/api/posts/{postId}")));
        assertTrue(routes.contains(route("GET", "/api/posts/{postId}/replies")));
        assertTrue(routes.contains(route("POST", "/api/posts/{postId}/reply")));
        assertTrue(routes.contains(route("POST", "/api/posts/{postId}/reshare")));
        assertTrue(routes.contains(route("PATCH", "/api/posts/{postId}")));
        assertTrue(routes.contains(route("DELETE", "/api/posts/{postId}")));
        assertTrue(routes.contains(route("POST", "/api/search/question")));
        assertTrue(routes.contains(route("POST", "/api/search/ai")));
        assertTrue(routes.contains(route("POST", "/api/images/generate")));

        assertFalse(routes.contains(route("GET", "/api/aes/key")));
        assertFalse(routes.contains(route("GET", "/api/get/image")));
    }

    @Test
    void structuredSpringDtosRemainThePublishedResponseShape() {
        ActionResponse actionResponse = new ActionResponse(
            Verbs.Action.LIKE,
            "post-1",
            List.of(new ActionActor("u1", "alice", "Alice")),
            1,
            5
        );
        MembersResponse membersResponse = new MembersResponse(
            "followers",
            List.of(new MemberInfo("u1", "alice", "Alice")),
            7
        );

        TimelineEntry timelineEntry = new TimelineEntry();
        timelineEntry.setUuid("post-1");
        timelineEntry.setType("text");
        timelineEntry.setActorUid("u1");
        timelineEntry.setActorUsername("alice");

        TimelineResponse timelineResponse = new TimelineResponse(List.of(timelineEntry), 1, 9);

        JsonNode actionNode = objectMapper.valueToTree(actionResponse);
        JsonNode membersNode = objectMapper.valueToTree(membersResponse);
        JsonNode timelineNode = objectMapper.valueToTree(timelineResponse);
        JsonNode firstTimelineEntry = timelineNode.path("entities").get(0);

        assertTrue(actionNode.has("actionType"));
        assertTrue(actionNode.has("actors"));
        assertFalse(actionNode.has("likes"));

        assertTrue(membersNode.has("setType"));
        assertTrue(membersNode.has("members"));
        assertFalse(membersNode.has("followers"));

        assertTrue(timelineNode.has("entities"));
        assertFalse(timelineNode.has("activity"));
        assertTrue(firstTimelineEntry.has("actorUid"));
        assertTrue(firstTimelineEntry.has("actorUsername"));
        assertFalse(firstTimelineEntry.has("actor"));
        assertFalse(firstTimelineEntry.has("activity"));
    }

    private static Set<Route> collectRoutes(Class<?>... controllers) {
        Set<Route> routes = new LinkedHashSet<>();
        for (Class<?> controller : controllers) {
            String[] basePaths = mappingPaths(
                controller.getAnnotation(RequestMapping.class) != null
                    ? controller.getAnnotation(RequestMapping.class).value()
                    : new String[0],
                controller.getAnnotation(RequestMapping.class) != null
                    ? controller.getAnnotation(RequestMapping.class).path()
                    : new String[0]
            );

            for (Method method : controller.getDeclaredMethods()) {
                addRoutes(routes, basePaths, "GET", method.getAnnotation(GetMapping.class));
                addRoutes(routes, basePaths, "POST", method.getAnnotation(PostMapping.class));
                addRoutes(routes, basePaths, "PUT", method.getAnnotation(PutMapping.class));
                addRoutes(routes, basePaths, "PATCH", method.getAnnotation(PatchMapping.class));
                addRoutes(routes, basePaths, "DELETE", method.getAnnotation(DeleteMapping.class));

                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                if (requestMapping != null) {
                    String[] methodPaths = mappingPaths(requestMapping.value(), requestMapping.path());
                    RequestMethod[] methods = requestMapping.method().length == 0
                        ? new RequestMethod[] {RequestMethod.GET}
                        : requestMapping.method();
                    for (RequestMethod requestMethod : methods) {
                        for (String basePath : basePaths) {
                            for (String methodPath : methodPaths) {
                                routes.add(route(requestMethod.name(), normalizePath(basePath, methodPath)));
                            }
                        }
                    }
                }
            }
        }
        return routes;
    }

    private static void addRoutes(Set<Route> routes, String[] basePaths, String httpMethod, GetMapping mapping) {
        if (mapping != null) {
            addRoutes(routes, basePaths, httpMethod, mappingPaths(mapping.value(), mapping.path()));
        }
    }

    private static void addRoutes(Set<Route> routes, String[] basePaths, String httpMethod, PostMapping mapping) {
        if (mapping != null) {
            addRoutes(routes, basePaths, httpMethod, mappingPaths(mapping.value(), mapping.path()));
        }
    }

    private static void addRoutes(Set<Route> routes, String[] basePaths, String httpMethod, PutMapping mapping) {
        if (mapping != null) {
            addRoutes(routes, basePaths, httpMethod, mappingPaths(mapping.value(), mapping.path()));
        }
    }

    private static void addRoutes(Set<Route> routes, String[] basePaths, String httpMethod, PatchMapping mapping) {
        if (mapping != null) {
            addRoutes(routes, basePaths, httpMethod, mappingPaths(mapping.value(), mapping.path()));
        }
    }

    private static void addRoutes(Set<Route> routes, String[] basePaths, String httpMethod, DeleteMapping mapping) {
        if (mapping != null) {
            addRoutes(routes, basePaths, httpMethod, mappingPaths(mapping.value(), mapping.path()));
        }
    }

    private static void addRoutes(Set<Route> routes, String[] basePaths, String httpMethod, String[] methodPaths) {
        for (String basePath : basePaths) {
            for (String methodPath : methodPaths) {
                routes.add(route(httpMethod, normalizePath(basePath, methodPath)));
            }
        }
    }

    private static String[] mappingPaths(String[] value, String[] path) {
        if (value.length > 0) {
            return value;
        }
        if (path.length > 0) {
            return path;
        }
        return new String[] {""};
    }

    private static String normalizePath(String basePath, String methodPath) {
        String raw = (basePath == null ? "" : basePath) + (methodPath == null ? "" : methodPath);
        String normalized = raw.replaceAll("//+", "/");
        if (normalized.isEmpty()) {
            return "/";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static Route route(String method, String path) {
        return new Route(method, path);
    }

    private record Route(String method, String path) {
    }
}
