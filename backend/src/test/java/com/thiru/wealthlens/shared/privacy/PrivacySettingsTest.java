package com.thiru.wealthlens.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Period;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The privacy settings refuse a start that could not answer r.9 or r.14(1) (W-237a; W-235 C5a's rule, now app-wide). */
class PrivacySettingsTest {

    private static final List<PrivacyRight> RIGHTS = List.of(
            new PrivacyRight("Erasure of your personal data", "s.12(1), (3)", "Write to the contact", null));

    private static final GrievancePeriod PERIOD = new GrievancePeriod(30, 90);

    private static final BreachReporting BREACH = new BreachReporting(72);

    private static final Period HOLD = Period.ofYears(1);

    private static final Period LAPSE = Period.ofYears(3);

    @Test
    void new_whenTheContactIsBlank_isRefusedNamingTheVariable() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings(" ", "your email", RIGHTS, PERIOD, BREACH, HOLD, HOLD, LAPSE))
                .withMessageContaining("PRIVACY_CONTACT");
    }

    @Test
    void new_whenNoRightIsPublished_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", List.of(), PERIOD, BREACH, HOLD, HOLD, LAPSE))
                .withMessageContaining("r.14(1)");
    }

    @Test
    void notice_whenSettingsAreComplete_publishesEachRightTheIdentifierAndTheContact() {
        // Given
        PrivacySettings settings = new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD, BREACH, HOLD, HOLD, LAPSE);

        // When
        PrivacyNotice notice = settings.notice();

        // Then
        assertThat(notice.contact()).isEqualTo("dpo@example.com");
        assertThat(notice.identifier()).isEqualTo("your email");
        assertThat(notice.rights()).singleElement().satisfies(right -> assertThat(right.endpoints()).isEmpty());
        assertThat(notice.grievanceResponseDays()).isEqualTo(30);
        assertThat(notice.purposeLapse()).isEqualTo(Period.ofYears(3));
    }

    @Test
    void new_whenNoPurposeLapseIsSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, HOLD, HOLD, null)).withMessageContaining("app.privacy.purpose-lapse");
    }

    @Test
    void new_whenThePurposeLapseIsShorterThanTheHold_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, HOLD, HOLD, Period.ofMonths(6))).withMessageContaining("r.8(3)");
    }

    @Test
    void new_whenThePurposeLapseEqualsTheHoldInOtherUnits_isRefused() {
        // Given twelve months against a year: equal, though Period.equals says otherwise
        // When / Then
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, HOLD, HOLD, Period.ofMonths(12))).withMessageContaining("r.8(3)");
    }

    @Test
    void new_whenThePurposeLapseIsOneMonthLongerThanTheHold_isAccepted() {
        assertThat(new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD, BREACH, HOLD, HOLD, Period.ofMonths(13))
                .purposeLapse()).isEqualTo(Period.ofMonths(13));
    }

    @Test
    void new_whenNoGrievancePeriodIsSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, null, BREACH, HOLD, HOLD, LAPSE))
                .withMessageContaining("r.14(3)");
    }

    @Test
    void new_whenNoBreachReportingIsSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                null, HOLD, HOLD, LAPSE)).withMessageContaining("r.7(2)(b)");
    }

    @Test
    void new_whenNoErasureHoldIsSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, null, HOLD, LAPSE)).withMessageContaining("r.8(3)");
    }

    @Test
    void new_whenTheErasureHoldIsZero_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, Period.ZERO, HOLD, LAPSE)).withMessageContaining("r.8(3)");
    }

    @Test
    void new_whenNoLogRetentionIsSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, HOLD, null, LAPSE)).withMessageContaining("app.privacy.log-retention");
    }

    @Test
    void new_whenTheLogRetentionIsZero_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", RIGHTS, PERIOD,
                BREACH, HOLD, Period.ZERO, LAPSE)).withMessageContaining("r.6(1)(e)");
    }

    @Test
    void breachReporting_whenNoHoursAreSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new BreachReporting(0)).withMessageContaining("at least one hour");
    }

    @Test
    void grievancePeriod_whenThePromiseExceedsTheLawsLimit_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new GrievancePeriod(91, 90))
                .withMessageContaining("exceeds the longest period the law allows (90 days");
    }

    @Test
    void grievancePeriod_whenThePromiseEqualsTheLawsLimit_isAccepted() {
        assertThat(new GrievancePeriod(90, 90).responseDays()).isEqualTo(90);
    }

    @Test
    void grievancePeriod_whenNoDaysAreSet_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new GrievancePeriod(0, 90)).withMessageContaining("at least one day");
    }
}
