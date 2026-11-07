package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * What a user is told before asking for anything (W-237a): each right and how to exercise it (Rules r.14(1)(a)), what
 * identifies them (r.14(1)(b)), who answers questions about the processing of their data (r.9), and within how many days
 * of its receipt a grievance is answered (r.14(3); W-237b).
 */
public record PrivacyNotice(List<PrivacyRight> rights, String identifier, String contact, int grievanceResponseDays) {

    public PrivacyNotice {
        rights = List.copyOf(rights);
    }
}
