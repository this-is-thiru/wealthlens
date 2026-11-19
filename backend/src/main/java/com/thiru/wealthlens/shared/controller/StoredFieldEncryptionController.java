package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.protection.StoredFieldEncryption;
import com.thiru.wealthlens.shared.protection.StoredFieldEncryptionReport;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Encrypts the personal data stored in plain text before W-248 (D-479 C3a). Asked for, never automatic (D-280): a
 * {@code SUPER_USER} calls it, a dry run first. One-time: removed once its run is logged for every database (W-254).
 */
@RequiredArgsConstructor
@RestController
public class StoredFieldEncryptionController {

    private final StoredFieldEncryption encryption;

    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/privacy/stored-fields/encrypt")
    public StoredFieldEncryptionReport encrypt(@RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
        return encryption.encrypt(dryRun, principal.getName());
    }
}
