package com.sih.sif.controller;

import com.sih.sif.model.User;
import com.sih.sif.repository.UserRepository;
import com.sih.sif.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AuthController.java
 *
 * Exposes authentication operations:
 * - POST /api/auth/login: verifies credentials and issues signed JWT.
 * - POST /api/auth/register: allows ADMIN to register new users with RBAC roles.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

@PostConstruct
public void seedDefaultAdmin() {
    User admin = userRepository.findByUsername(adminUsername).orElse(null);

    if (admin == null) {
        log.info("Admin user '{}' not found. Creating default admin.", adminUsername);

        admin = new User(
                adminUsername,
                passwordEncoder.encode(adminPassword),
                "ROLE_ADMIN"
        );

        userRepository.save(admin);
    } else {
        log.info("Admin user '{}' already exists. Updating configured password.", adminUsername);

        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ROLE_ADMIN");

        userRepository.save(admin);
    }
}

    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RegisterRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        @NotBlank
        private String role; // "ROLE_ADMIN", "ROLE_SAFETY_OFFICER", "ROLE_FIELD_WORKER"

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Unauthorized");
            err.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("username", user.getUsername());
        resp.put("role", user.getRole());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Conflict");
            err.put("message", "Username already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
        }

        String role = request.getRole().startsWith("ROLE_") ? request.getRole() : "ROLE_" + request.getRole();
        User newUser = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()), role);
        userRepository.save(newUser);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "User created successfully");
        resp.put("username", newUser.getUsername());
        resp.put("role", newUser.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
