package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.model.AuthResponse;
import com.intelligenta.socialgraph.service.SessionService;
import com.intelligenta.socialgraph.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final SessionService sessionService;

    public AuthController(UserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    /**
     * User login.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestParam String username,
            @RequestParam String password) {
        AuthResponse response = userService.login(username, password);
        return ResponseEntity.ok(response);
    }

    /**
     * User registration.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email) throws NoSuchAlgorithmException {
        AuthResponse response = userService.register(username, password, email);
        return ResponseEntity.ok(response);
    }

    /**
     * Account activation.
     */
    @GetMapping("/activate")
    public ResponseEntity<Map<String, String>> activate(@RequestParam String token) {
        Map<String, String> result = userService.activateAccount(token);
        if ("false".equals(result.get("activated"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Session endpoint for key exchange.
     */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(
            @RequestParam(required = false) String uuid) {
        return ResponseEntity.ok(sessionService.getSession(uuid));
    }

    /**
     * Simple ping endpoint for health checks.
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("hello");
    }
}
