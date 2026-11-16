package com.civilmate.api.auth;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.civilmate.api.auth.dto.ApiEnvelope;
import com.civilmate.api.auth.dto.LoginRequest;
import com.civilmate.api.auth.dto.RegisterRequest;
import com.civilmate.api.auth.dto.UserResponse;
import com.civilmate.api.auth.service.AuthService;
import com.civilmate.api.auth.service.AuthService.IssuedSession;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public static final String REFRESH_COOKIE = "civilmate_refresh";
    private final AuthService authService;
    private final long refreshTtlDays;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
            @Value("${security.refresh-token-ttl-days:7}") long refreshTtlDays,
            @Value("${security.refresh-cookie-secure:false}") boolean secureCookie) {
        this.authService = authService;
        this.refreshTtlDays = refreshTtlDays;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiEnvelope<?>> register(@RequestBody @jakarta.validation.Valid RegisterRequest request) {
        return sessionResponse(authService.register(request), "Account created successfully.", HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiEnvelope<?>> login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
        return sessionResponse(authService.login(request), "Signed in successfully.", HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiEnvelope<?>> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return sessionResponse(authService.refresh(refreshToken), "Session refreshed successfully.", HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(ApiEnvelope.success("Signed out successfully.", Map.of("loggedOut", true)));
    }

    @GetMapping("/me")
    public ApiEnvelope<UserResponse> currentUser(@AuthenticationPrincipal Jwt jwt) {
        return ApiEnvelope.success("Current user retrieved successfully.", authService.currentUser(jwt.getSubject()));
    }

    private ResponseEntity<ApiEnvelope<?>> sessionResponse(IssuedSession session, String message, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), Duration.ofDays(refreshTtlDays)).toString())
                .body(ApiEnvelope.success(message, session.data()));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
