package com.thiru.wealthlens.shared.privacy;

/**
 * How long after becoming aware of a personal data breach the detailed report is due to the Board (W-237c). The
 * Digital Personal Data Protection Rules, 2025 r.7(2)(b) set seventy-two hours, or longer if the Board allows; the
 * figure is configuration so a change in the law is a change of data.
 *
 * @param boardReportHours hours from awareness to the Board's detailed report
 */
public record BreachReporting(int boardReportHours) {

    public BreachReporting {
        if (boardReportHours < 1) {
            throw new IllegalStateException("app.privacy.breach.board-report-hours must be at least one hour"
                    + " (DPDP Rules r.7(2)(b))");
        }
    }
}
