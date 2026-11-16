package com.civilmate.api.auth.dto;

public record AuthData(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) { }
