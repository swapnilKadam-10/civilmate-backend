package com.civilmate.api.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.auth.dto.AuthData;
import com.civilmate.api.auth.dto.LoginRequest;
import com.civilmate.api.auth.dto.RegisterRequest;
import com.civilmate.api.auth.dto.UserResponse;
import com.civilmate.api.auth.entity.RefreshToken;
import com.civilmate.api.auth.entity.UserAccount;
import com.civilmate.api.auth.repository.RefreshTokenRepository;
import com.civilmate.api.auth.repository.UserAccountRepository;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, Clock clock,
            @Value("${security.jwt.access-token-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${security.refresh-token-ttl-days:7}") long refreshTtlDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public IssuedSession register(RegisterRequest request) {
        validatePasswordBytes(request.password());
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        UserAccount user = users.save(new UserAccount(
                request.fullName().trim(), email, passwordEncoder.encode(request.password()), clock.instant()));
        return issueSession(user);
    }

    @Transactional
    public IssuedSession login(LoginRequest request) {
        validatePasswordBytes(request.password());
        UserAccount user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(UserAccount::isActive)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
        user.recordLogin(clock.instant());
        users.save(user);
        return issueSession(user);
    }

    @Transactional
    public IssuedSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        RefreshToken existing = refreshTokens.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        Instant now = clock.instant();
        if (existing.getRevokedAt() != null || !existing.getExpiresAt().isAfter(now) || !existing.getUser().isActive()) {
            throw invalidRefreshToken();
        }
        existing.revoke(now);
        refreshTokens.save(existing);
        return issueSession(existing.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(clock.instant());
                refreshTokens.save(token);
            }
        });
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token.");
        }
        return users.findById(id).filter(UserAccount::isActive).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
    }

    private IssuedSession issueSession(UserAccount user) {
        Instant now = clock.instant();
        Instant accessExpiry = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("civilmate")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] tokenBytes = new byte[48];
        RANDOM.nextBytes(tokenBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        refreshTokens.save(new RefreshToken(user, hashToken(rawRefreshToken),
                now.plus(refreshTtlDays, ChronoUnit.DAYS), now));
        return new IssuedSession(new AuthData(accessToken, "Bearer", accessTtlMinutes * 60, UserResponse.from(user)), rawRefreshToken);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be no more than 72 UTF-8 bytes.");
        }
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired.");
    }

    public record IssuedSession(AuthData data, String refreshToken) { }
}
