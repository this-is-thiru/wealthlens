package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A form refuses what could never be chosen or furnished, and a cascade it could not take part in (W-090b). */
class ItrFormSpecTest {

    private static final ItrFormCode FORM = new ItrFormCode("SYN-1");
    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-090b"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: synthetic");
    private static final List<FurnishingOption> ELECTRONIC =
            List.of(new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), SOURCE));

    private static ItrFormSpec form(int order, Set<PersonType> appliesTo, Set<ItrFormCode> excludes,
            List<FurnishingOption> furnishing) {
        return new ItrFormSpec(FORM, "Synthetic form", order, appliesTo, Set.of(new ReturnKind("SYN_ORIGINAL")),
                List.of(new IsResident()), List.of(), excludes, furnishing, List.of(), SOURCE);
    }

    @Test
    void constructor_whenEveryPartIsGiven_keepsThem() {
        // Given / When
        ItrFormSpec form = form(1, Set.of(PersonType.INDIVIDUAL), Set.of(), ELECTRONIC);

        // Then
        assertThat(form.cascadeOrder()).isEqualTo(1);
        assertThat(form.furnishing()).extracting(FurnishingOption::manner)
                .containsExactly(new FurnishingManner("SYN_ELECTRONIC"));
    }

    @Test
    void constructor_whenCascadeOrderIsBelowOne_refuses() {
        assertThatThrownBy(() -> form(0, Set.of(PersonType.INDIVIDUAL), Set.of(), ELECTRONIC))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SYN-1 has cascade order 0");
    }

    @Test
    void constructor_whenItServesNoPersonType_refuses() {
        assertThatThrownBy(() -> form(1, Set.of(), Set.of(), ELECTRONIC))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SYN-1.appliesTo is empty");
    }

    @Test
    void constructor_whenItHasNoMannerOfFurnishing_refuses() {
        assertThatThrownBy(() -> form(1, Set.of(PersonType.INDIVIDUAL), Set.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SYN-1.furnishing is empty");
    }

    @Test
    void constructor_whenItExcludesItself_refuses() {
        assertThatThrownBy(() -> form(1, Set.of(PersonType.INDIVIDUAL), Set.of(FORM), ELECTRONIC))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SYN-1 excludes itself");
    }

    @Test
    void barConstructor_whenTheReasonIsBlank_refuses() {
        assertThatThrownBy(() -> new ItrFormBar(" ", new IsResident(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must state its reason");
    }

    /** Every part given, any one replaced by the test: the constructor's refusals one at a time (W-265). */
    private static ItrFormSpec spec(ItrFormCode code, String displayName, List<Condition> positive, Provenance provenance) {
        return new ItrFormSpec(code, displayName, 1, Set.of(PersonType.INDIVIDUAL), Set.of(new ReturnKind("SYN_ORIGINAL")),
                positive, List.of(), Set.of(), ELECTRONIC, List.of(), provenance);
    }

    @Test
    void constructor_whenTheCodeIsMissing_refuses() {
        assertThatThrownBy(() -> spec(null, "Synthetic form", List.of(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A form must have a code");
    }

    @Test
    void constructor_whenTheDisplayNameIsMissingOrBlank_refuses() {
        assertThatThrownBy(() -> spec(FORM, null, List.of(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SYN-1 must have a display name");
        assertThatThrownBy(() -> spec(FORM, " ", List.of(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SYN-1 must have a display name");
    }

    @Test
    void constructor_whenACollectionIsNull_refusesNamingIt() {
        assertThatThrownBy(() -> spec(FORM, "Synthetic form", null, SOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SYN-1.positiveConditions may not be null; pass an empty collection");
    }

    @Test
    void constructor_whenTheSourceIsMissing_refuses() {
        assertThatThrownBy(() -> spec(FORM, "Synthetic form", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SYN-1 must say where it was read from");
    }

    @Test
    void barConstructor_whenTheReasonIsMissing_refuses() {
        assertThatThrownBy(() -> new ItrFormBar(null, new IsResident(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must state its reason");
    }

    @Test
    void barConstructor_whenTheConditionOrSourceIsMissing_refuses() {
        assertThatThrownBy(() -> new ItrFormBar("a reason", null, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must carry its condition and its source");
        assertThatThrownBy(() -> new ItrFormBar("a reason", new IsResident(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must carry its condition and its source");
    }

    @Test
    void furnishingConstructor_whenTheMannerOrSourceIsMissing_refuses() {
        assertThatThrownBy(() -> new FurnishingOption(null, List.of(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A manner of furnishing must name itself and its source");
        assertThatThrownBy(() -> new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A manner of furnishing must name itself and its source");
    }

    @Test
    void furnishingConstructor_whenTheConditionsAreNull_refuses() {
        assertThatThrownBy(() -> new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), null, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conditions may not be null");
    }
}
