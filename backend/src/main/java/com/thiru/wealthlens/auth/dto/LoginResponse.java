package com.thiru.wealthlens.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "from")
public class LoginResponse {
	private String access_token;
	private int expires_in;
	private final String tokenType = "Bearer";
	/** The privacy notice's page: what a person registered before W-250 is told at sign-in (Act s.5(2)(a)). */
	@JsonProperty("privacy_notice")
	private String privacyNotice;
	@JsonProperty("privacy_notice_version")
	private String privacyNoticeVersion;
}
