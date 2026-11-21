package com.thiru.wealthlens.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A rotated pair (W-358b): the new access token, its lifetime in seconds, and the refresh token that replaces the one presented. */
public record RefreshResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresInSeconds,
                              @JsonProperty("refresh_token") String refreshToken) {

    /** As the sign-in's {@code tokenType}. */
    @JsonProperty("tokenType")
    public String tokenType() {
        return "Bearer";
    }
}
