package com.thiru.wealthlens.shared.protection;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The two keys that protect stored personal data ({@code DR 14} O1.1), each 32 bytes in base64: one encrypts, one digests the
 * identifiers that must stay unique. Separate so that a digest, which is stable, never reveals anything about the cipher's key.
 */
@ConfigurationProperties(prefix = "app.personal-data")
public record PersonalDataKeys(String dataKey, String digestKey) {
}
