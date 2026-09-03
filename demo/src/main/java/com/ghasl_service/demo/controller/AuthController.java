package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.AuthRequest;
import com.ghasl_service.demo.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody AuthRequest loginRequest, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String jwt = jwtUtil.generateToken(userDetails);

        Cookie jwtCookie = new Cookie("jwt", jwt);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false); // Set to true in production with HTTPS
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(24 * 60 * 60); // 1 day
        jwtCookie.setAttribute("SameSite", "Strict"); // Changed from Lax to Strict for CSRF protection

        response.addCookie(jwtCookie);

        return ResponseEntity.ok(Map.of("status", "success", "message", "User authenticated successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletResponse response) {
        Cookie jwtCookie = new Cookie("jwt", null);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false); // Match login cookie setting (set to true in production with HTTPS)
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0); // clear cookie
        jwtCookie.setAttribute("SameSite", "Strict"); // Match login cookie setting

        response.addCookie(jwtCookie);

        return ResponseEntity.ok(Map.of("status", "success", "message", "User logged out successfully"));
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkAuth(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(Map.of("status", "authenticated", "username", authentication.getName()));
        }
        return ResponseEntity.status(401).body(Map.of("status", "unauthenticated"));
    }
}
