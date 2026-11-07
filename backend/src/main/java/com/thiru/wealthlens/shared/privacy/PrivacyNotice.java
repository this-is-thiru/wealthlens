package com.thiru.wealthlens.shared.privacy;

import java.time.Period;
import java.util.List;

/**
 * What a user is told before asking for anything (W-237a): each right and how to exercise it (Rules r.14(1)(a)), what
 * identifies them (r.14(1)(b)), who answers questions about the processing of their data (r.9), and within how many days
 * of its receipt a grievance is answered (r.14(3); W-237b), and how long after the last sign-in an account is erased because
 * its purpose is no longer served (Act s.8(7)(a); W-249).
 */
public record PrivacyNotice(List<PrivacyRight> rights, String identifier, String contact, int grievanceResponseDays,
        Period purposeLapse) {

    public PrivacyNotice {
        rights = List.copyOf(rights);
    }
}
