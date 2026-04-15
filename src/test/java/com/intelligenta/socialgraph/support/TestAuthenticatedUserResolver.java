package com.intelligenta.socialgraph.support;

import com.intelligenta.socialgraph.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public final class TestAuthenticatedUserResolver implements HandlerMethodArgumentResolver {

    static final String UID_ATTRIBUTE = TestAuthenticatedUserResolver.class.getName() + ".uid";
    static final String USERNAME_ATTRIBUTE = TestAuthenticatedUserResolver.class.getName() + ".username";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        String uid = (String) request.getAttribute(UID_ATTRIBUTE);
        if (uid == null) {
            return null;
        }

        String username = (String) request.getAttribute(USERNAME_ATTRIBUTE);
        return new AuthenticatedUser(uid, username != null ? username : "tester");
    }
}
