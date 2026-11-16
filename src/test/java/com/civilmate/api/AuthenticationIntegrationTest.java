package com.civilmate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build(); }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void migrationsAndAuthenticationLifecycleMeetPhaseTwoRequirements() throws Exception {
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class));
        assertEquals(1, tableCount("users"));
        assertEquals(1, tableCount("refresh_tokens"));

        TestResponse registration = send("POST", "/api/v1/auth/register",
                "{\"fullName\":\"Test Student\",\"email\":\"student@example.test\",\"password\":\"civilmate-password\"}", null, null);
        assertEquals(201, registration.statusCode());
        assertTrue(registration.body().contains("\"role\":\"USER\""), registration.body());
        String registrationAccess = accessToken(registration.body());
        String registrationCookie = cookiePair(registration);
        assertCookieSecurityFlags(registration, false);

        TestResponse currentUser = send("GET", "/api/v1/auth/me", null, null, registrationAccess);
        assertEquals(200, currentUser.statusCode());
        assertTrue(currentUser.body().contains("student@example.test"));

        assertEquals(401, send("GET", "/api/v1/auth/me", null, null, null).statusCode());
        assertEquals(401, send("GET", "/api/v1/auth/me", null, null, "not-a-jwt").statusCode());
        assertEquals(403, send("GET", "/api/v1/admin/probe", null, null, registrationAccess).statusCode());
        assertEquals(401, send("GET", "/api/v1/admin/probe", null, null, null).statusCode());
        assertEquals(401, send("GET", "/api/v1/auth/me", null, null, expiredJwt()).statusCode());

        TestResponse login = send("POST", "/api/v1/auth/login",
                "{\"email\":\"STUDENT@example.test\",\"password\":\"civilmate-password\"}", null, null);
        assertEquals(200, login.statusCode());
        String loginAccess = accessToken(login.body());
        String oldRefreshCookie = cookiePair(login);
        assertCookieSecurityFlags(login, false);
        assertEquals(401, send("POST", "/api/v1/auth/login",
                "{\"email\":\"student@example.test\",\"password\":\"wrong-password\"}", null, null).statusCode());

        TestResponse refresh = send("POST", "/api/v1/auth/refresh", "{}", oldRefreshCookie, null);
        assertEquals(200, refresh.statusCode());
        String rotatedRefreshCookie = cookiePair(refresh);
        assertNotEquals(oldRefreshCookie, rotatedRefreshCookie);
        assertCookieSecurityFlags(refresh, false);
        assertTrue(isRefreshTokenHashStored(tokenValue(oldRefreshCookie)));
        assertTrue(isRefreshTokenRevoked(tokenValue(oldRefreshCookie)));
        assertFalse(isRawRefreshTokenStored(tokenValue(oldRefreshCookie)));
        assertEquals(401, send("POST", "/api/v1/auth/refresh", "{}", oldRefreshCookie, null).statusCode());

        TestResponse logout = send("POST", "/api/v1/auth/logout", "{}", rotatedRefreshCookie, loginAccess);
        assertEquals(200, logout.statusCode());
        assertTrue(logout.headers().firstValue("set-cookie").orElseThrow().contains("Max-Age=0"));
        assertCookieSecurityFlags(logout, true);
        assertTrue(isRefreshTokenRevoked(tokenValue(rotatedRefreshCookie)));
        assertEquals(401, send("POST", "/api/v1/auth/refresh", "{}", rotatedRefreshCookie, null).statusCode());
        assertFalse(isRawRefreshTokenStored(tokenValue(registrationCookie)));
    }

    private int tableCount(String tableName) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?", Integer.class, tableName);
    }

    private boolean isRefreshTokenHashStored(String rawToken) throws Exception {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, sha256(rawToken)) == 1;
    }

    private boolean isRefreshTokenRevoked(String rawToken) throws Exception {
        return jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?", Boolean.class, sha256(rawToken));
    }

    private boolean isRawRefreshTokenStored(String rawToken) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, rawToken) == 1;
    }

    private String expiredJwt() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("civilmate").subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(60)).claim("roles", java.util.List.of("USER")).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private TestResponse send(String method, String path, String body, String cookie, String bearer) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).accept(MediaType.APPLICATION_JSON);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (cookie != null) request.header("Cookie", cookie);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        var response = mockMvc.perform(request).andReturn().getResponse();
        return new TestResponse(response.getStatus(), response.getContentAsString(), response.getHeader("Set-Cookie"));
    }

    private record TestResponse(int statusCode, String body, String setCookie) { TestHeaders headers() { return new TestHeaders(setCookie); } }
    private record TestHeaders(String setCookie) { Optional<String> firstValue(String name) { return "set-cookie".equalsIgnoreCase(name) ? Optional.ofNullable(setCookie) : Optional.empty(); } }

    private static String accessToken(String body) {
        Matcher match = ACCESS_TOKEN.matcher(body);
        assertTrue(match.find(), "Response must contain an access token: " + body);
        return match.group(1);
    }

    private static String cookiePair(TestResponse response) {
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private static String tokenValue(String cookiePair) {
        return cookiePair.substring(cookiePair.indexOf('=') + 1);
    }

    private static void assertCookieSecurityFlags(TestResponse response, boolean expired) {
        String cookie = response.headers().firstValue("set-cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("Secure"), cookie);
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
        assertTrue(cookie.contains("Path=/api/v1/auth"), cookie);
        if (expired) assertTrue(cookie.contains("Max-Age=0"), cookie);
    }

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
