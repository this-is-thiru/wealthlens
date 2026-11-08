package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.ErasureReport;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A person's holdings, trades, P&amp;L, charges, charge accounts and corporate-action watermarks, erased with their account
 * (W-237d) through {@link UserRecordsErasureService}, the one list of what a user's records are. A delete that fails fails the
 * erasure, so the request stays open and the next run completes it.
 */
@Component
@RequiredArgsConstructor
public class PortfolioRecordErasure implements PersonalDataHolder {

    private final UserRecordsErasureService userRecordsErasureService;

    @Override
    public String noticePart() {
        return "portfolio";
    }

    @Override
    public void erase(String email) {
        ErasureReport report = userRecordsErasureService.erase(UserMail.from(email));
        if (!report.isComplete()) {
            throw new IllegalStateException("Account erasure left " + report.getFailed() + " behind");
        }
    }
}
