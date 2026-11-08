package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link CapitalGainsInput} refuses, one bad field at a time; each message is the record's own (W-240b). */
class CapitalGainsInputTest {

    private static final LocalDate BOUGHT = LocalDate.of(2024, 4, 1);
    private static final LocalDate SOLD = LocalDate.of(2026, 6, 1);
    private static final CapitalGainsInput.SideVenue VENUE = CapitalGainsInput.SideVenue.UNRECORDED;
    private static final CapitalGainsInput.BucketFacts FACTS = new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), BOUGHT, SOLD, VENUE, VENUE);
    private static final Money CONSIDERATION = Money.of("1000");
    private static final Instant AS_OF = Instant.parse("2026-11-08T00:00:00Z");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        CapitalGainsInput valid = new CapitalGainsInput(List.of(new CapitalGainsInput.Bucket(CONSIDERATION, Money.ZERO, List.of(), FACTS, Optional.empty())), AS_OF, "portfolio-1");

        // Then
        assertThat(valid.buckets()).hasSize(1);
    }

    static Stream<Arguments> refusals() {
        String bucket = "A capital-gains bucket must carry its consideration, its cost and its charges";
        String charge = "A charge needs its side, code and amount";
        String bucketFacts = "A bucket's facts need its asset kind, STT evidence on both sides";
        CapitalGainsInput.ChargeSide side = CapitalGainsInput.ChargeSide.TRANSFER;
        return Stream.of(
                refusal("bucket consideration null", () -> new CapitalGainsInput.Bucket(null, Money.ZERO, List.of(), FACTS, Optional.empty()),
                        bucket),
                refusal("bucket cost null", () -> new CapitalGainsInput.Bucket(CONSIDERATION, null, List.of(), FACTS, Optional.empty()),
                        bucket),
                refusal("bucket charges null", () -> new CapitalGainsInput.Bucket(CONSIDERATION, Money.ZERO, null, FACTS, Optional.empty()),
                        bucket),
                refusal("bucket portfolioRuleRef null", () -> new CapitalGainsInput.Bucket(CONSIDERATION, Money.ZERO, List.of(), FACTS, null),
                        "Bucket.portfolioRuleRef may not be null"),
                refusal("charge side null", () -> new CapitalGainsInput.Charge(null, "STT", Money.ZERO),
                        charge),
                refusal("charge code null", () -> new CapitalGainsInput.Charge(side, null, Money.ZERO),
                        charge),
                refusal("charge code blank", () -> new CapitalGainsInput.Charge(side, " ", Money.ZERO),
                        charge),
                refusal("charge amount null", () -> new CapitalGainsInput.Charge(side, "STT", null),
                        charge),
                refusal("charge amount negative", () -> new CapitalGainsInput.Charge(side, "STT", Money.of("-1")),
                        "The charge STT on the TRANSFER is negative"),
                refusal("facts assetKind null", () -> new CapitalGainsInput.BucketFacts(null, "PAID", "PAID", "PURCHASE", Optional.empty(), BOUGHT, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts assetKind blank", () -> new CapitalGainsInput.BucketFacts(" ", "PAID", "PAID", "PURCHASE", Optional.empty(), BOUGHT, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts sttOnAcquisition null", () -> new CapitalGainsInput.BucketFacts("EQUITY", null, "PAID", "PURCHASE", Optional.empty(), BOUGHT, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts sttOnTransfer null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", null, "PURCHASE", Optional.empty(), BOUGHT, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts acquisitionMode null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", null, Optional.empty(), BOUGHT, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts corporateAction null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", null, BOUGHT, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts acquiredOn null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), null, SOLD, VENUE, VENUE),
                        bucketFacts),
                refusal("facts transferredOn null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), BOUGHT, null, VENUE, VENUE),
                        bucketFacts),
                refusal("facts acquisitionVenue null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), BOUGHT, SOLD, null, VENUE),
                        bucketFacts),
                refusal("facts transferVenue null", () -> new CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), BOUGHT, SOLD, VENUE, null),
                        bucketFacts),
                refusal("venue exchange null", () -> new CapitalGainsInput.SideVenue(null, Optional.empty()),
                        "A side venue needs its exchange and segment"),
                refusal("venue segment null", () -> new CapitalGainsInput.SideVenue(Optional.empty(), null),
                        "A side venue needs its exchange and segment"),
                refusal("buckets null", () -> new CapitalGainsInput(null, AS_OF, "portfolio-1"),
                        "CapitalGainsInput buckets may not be null"),
                refusal("computation reference null", () -> new CapitalGainsInput(List.of(), AS_OF, null),
                        "CapitalGainsInput must carry the portfolio's computation reference"),
                refusal("computation reference blank", () -> new CapitalGainsInput(List.of(), AS_OF, " "),
                        "CapitalGainsInput must carry the portfolio's computation reference"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
