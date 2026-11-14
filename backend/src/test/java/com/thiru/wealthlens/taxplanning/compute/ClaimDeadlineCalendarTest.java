package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.catalogue.ClaimDeadline;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * I26 (W-117a C1a, D-699): every deadline a claim step can name is a day the draft calendar works out, or one that has
 * no date. The closed tax engine cannot name the calendar's kinds, so this test holds the link: a deadline added to the
 * set fails here until it is placed.
 */
class ClaimDeadlineCalendarTest {

    /** Typed by hand from W-117a C1a: the tax year's end is the day a deduction must be paid by; two have no date. */
    private static final Map<ClaimDeadline, Optional<DraftCalendar.Kind>> ON_THE_CALENDAR = Map.of(
            ClaimDeadline.TAX_YEAR_END, Optional.of(DraftCalendar.Kind.DEDUCTION_PAID_BY),
            ClaimDeadline.RETURN_DUE, Optional.of(DraftCalendar.Kind.RETURN_DUE),
            ClaimDeadline.EMPLOYER_PROOF_CUT_OFF, Optional.empty(),
            ClaimDeadline.NONE, Optional.empty());

    @Test
    void values_whenEveryClaimDeadlineIsLookedUp_isOnTheCalendarOrUndated() {
        // When / Then
        assertThat(ON_THE_CALENDAR).containsOnlyKeys(ClaimDeadline.values());
        assertThat(ON_THE_CALENDAR.values().stream().flatMap(Optional::stream)).doesNotHaveDuplicates();
    }
}
