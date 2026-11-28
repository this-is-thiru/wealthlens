package com.thiru.wealthlens.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "from")
public class LoginResponse {
	@JsonProperty("access_token")
	private String accessToken;
	@JsonProperty("expires_in")
	private int expiresIn;
	/** Renews the access token for 14 days from this sign-in, rotated on each use (W-358b). */
	@JsonProperty("refresh_token")
	private String refreshToken;
	private final String tokenType = "Bearer";
	/** The privacy notice's page: what a person registered before W-250 is told at sign-in (Act s.5(2)(a)). */
	@JsonProperty("privacy_notice")
	private String privacyNotice;
	@JsonProperty("privacy_notice_version")
	private String privacyNoticeVersion;
}
