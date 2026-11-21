package com.thiru.wealthlens.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A refresh token presented to be rotated or ended (W-358b); refused on the way in when absent. */
public record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {

    public RefreshRequest {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refresh_token is required");
        }
    }
}
