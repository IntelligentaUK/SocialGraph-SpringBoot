package com.intelligenta.socialgraph.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestRequestPostProcessors {

    private TestRequestPostProcessors() {
    }

    public static RequestPostProcessor authenticatedUser(String uid) {
        return authenticatedUser(uid, "tester");
    }

    public static RequestPostProcessor authenticatedUser(String uid, String username) {
        return request -> {
            request.setAttribute(TestAuthenticatedUserResolver.UID_ATTRIBUTE, uid);
            request.setAttribute(TestAuthenticatedUserResolver.USERNAME_ATTRIBUTE, username);
            return request;
        };
    }
}
