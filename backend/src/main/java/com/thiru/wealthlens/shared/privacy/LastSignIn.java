package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;

/** An account and when it was last signed in to, in UTC (W-249). */
public record LastSignIn(String email, LocalDateTime signedInAt) {
}
