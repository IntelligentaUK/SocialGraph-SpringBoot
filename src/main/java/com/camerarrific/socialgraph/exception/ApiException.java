package com.camerarrific.socialgraph.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Custom exception for API errors.
 */
@Getter
public class ApiException extends RuntimeException {

    private final String error;
    private final String errorDescription;
    private final HttpStatus status;

    public ApiException(String error, String errorDescription, HttpStatus status) {
        super(errorDescription);
        this.error = error;
        this.errorDescription = errorDescription;
        this.status = status;
    }

    public ApiException(String error, String errorDescription) {
        this(error, errorDescription, HttpStatus.BAD_REQUEST);
    }

    // Predefined exceptions matching legacy error codes
    public static ApiException alreadyFollowing() {
        return new ApiException("cannot_follow", "Already following", HttpStatus.BAD_REQUEST);
    }

    public static ApiException cannotFollowYourself() {
        return new ApiException("cannot_follow", "Cannot follow yourself", HttpStatus.BAD_REQUEST);
    }

    public static ApiException cannotUnfollowYourself() {
        return new ApiException("cannot_unfollow", "Cannot unfollow yourself", HttpStatus.BAD_REQUEST);
    }

    public static ApiException followerMissing() {
        return new ApiException("incomplete_request", "Follower missing from request", HttpStatus.BAD_REQUEST);
    }

    public static ApiException tokenInvalid() {
        return new ApiException("auth_error", "Token invalid", HttpStatus.UNAUTHORIZED);
    }

    public static ApiException tokenMissing() {
        return new ApiException("incomplete_request", "Token missing from request", HttpStatus.BAD_REQUEST);
    }

    public static ApiException invalidCredentials() {
        return new ApiException("invalid_grant", "Invalid username or password", HttpStatus.UNAUTHORIZED);
    }

    public static ApiException passwordHashFailure() {
        return new ApiException("internal_server_error", "Password hash failure", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static ApiException notFollowing() {
        return new ApiException("cannot_unfollow", "Not following", HttpStatus.BAD_REQUEST);
    }

    public static ApiException alreadyRegistered() {
        return new ApiException("cannot_register", "Already registered", HttpStatus.BAD_REQUEST);
    }

    public static ApiException accountBanned() {
        return new ApiException("cannot_login", "User banned", HttpStatus.FORBIDDEN);
    }

    public static ApiException accountDisabled() {
        return new ApiException("cannot_login", "Account disabled", HttpStatus.FORBIDDEN);
    }

    public static ApiException temporaryLockout() {
        return new ApiException("cannot_login", "Temporary lockout", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static ApiException accountNotActivated() {
        return new ApiException("cannot_login", "Account not activated", HttpStatus.FORBIDDEN);
    }

    public static ApiException postNotFound() {
        return new ApiException("cannot_perform_action", "Post not found", HttpStatus.NOT_FOUND);
    }

    public static ApiException cannotFollowUnknownUser() {
        return new ApiException("cannot_follow", "Cannot follow unknown user", HttpStatus.BAD_REQUEST);
    }

    public static ApiException cannotUnfollowUnknownUser() {
        return new ApiException("cannot_unfollow", "Cannot unfollow unknown user", HttpStatus.BAD_REQUEST);
    }

    public static ApiException userNotFound() {
        return new ApiException("user_not_found", "User not found", HttpStatus.NOT_FOUND);
    }
}

