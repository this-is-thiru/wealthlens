package com.thiru.wealthlens.shared.privacy;

/**
 * How long a grievance may wait for its answer (W-237b). The Digital Personal Data Protection Rules, 2025 r.14(3) ask for a
 * published period that is reasonable and not more than ninety days; both figures are configuration, so a change in the
 * law or in the promise is a change of data. A start whose promise exceeds the law's limit is refused.
 *
 * @param responseDays the days, from receipt, within which a grievance is answered: what the notice and each receipt promise
 * @param longestDaysAllowed the most the law allows ({@code app.privacy.grievance.longest-days-allowed})
 */
public record GrievancePeriod(int responseDays, int longestDaysAllowed) {

    public GrievancePeriod {
        if (responseDays < 1 || longestDaysAllowed < 1) {
            throw new IllegalStateException("app.privacy.grievance.response-days and longest-days-allowed must both be set to"
                    + " at least one day (DPDP Rules r.14(3))");
        }
        if (responseDays > longestDaysAllowed) {
            throw new IllegalStateException("app.privacy.grievance.response-days (" + responseDays
                    + ") exceeds the longest period the law allows (" + longestDaysAllowed + " days, DPDP Rules r.14(3))");
        }
    }
}
