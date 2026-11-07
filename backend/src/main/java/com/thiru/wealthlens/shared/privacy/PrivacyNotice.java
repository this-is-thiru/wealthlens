package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * What a user is told before asking for anything (W-237a): each right and how to exercise it (Rules r.14(1)(a)), what
 * identifies them (r.14(1)(b)), and who answers questions about the processing of their data (r.9).
 */
public record PrivacyNotice(List<PrivacyRight> rights, String identifier, String contact) {

    public PrivacyNotice {
        rights = List.copyOf(rights);
    }
}
