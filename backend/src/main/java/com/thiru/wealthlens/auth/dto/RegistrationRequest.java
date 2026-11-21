package com.thiru.wealthlens.auth.dto;

import lombok.Data;

@Data
public class RegistrationRequest {
	private String email;
	private String password;
	private String newPassword;
	/** The version of the privacy notice the person read before consenting, from {@code GET /api/v1/public/privacy/rights} (W-250). */
	private String privacyNoticeVersion;
}
