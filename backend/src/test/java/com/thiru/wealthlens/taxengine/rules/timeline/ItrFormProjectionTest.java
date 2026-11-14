package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.Declared;
import com.thiru.wealthlens.taxengine.rules.condition.DeclaredAmountAbove;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormBar;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The return forms as each year sees them (W-090b), on the synthetic pack: read from {@code forms/itr-forms/},
 * compiled into the year's {@code forms/itr-forms.json} as the version in force, and assembled into the pack.
 * {@code SYN-1}'s bar reads {@code SYNTHETIC_YES_NO} until 2101-03-31 and {@code SYNTHETIC_AMOUNT} above ₹1,000 from
 * 2101-04-01, the first day of 2101-02; {@code SYN-2} is one version, excluded by {@code SYN-1}.
 */
class ItrFormProjectionTest {

    private static final String ROOT = "rulepacks-synthetic";
    private static final ShippedRulePacks PACKS =
            ShippedRulePacks.compile(ROOT, ClasspathRulePackProvider.shippedYears(ROOT));
    private static final ItrFormCode ONE = new ItrFormCode("SYN-1");
    private static final ItrFormCode TWO = new ItrFormCode("SYN-2");

    @Test
    void load_whenTheFirstYearIsLoaded_carriesEachFormWhole() {
        // Given / When
        var forms = PACKS.load(TaxYearId.of(2099)).itrForms();

        // Then
        assertThat(forms).containsOnlyKeys(ONE, TWO);
        ItrFormSpec one = forms.get(ONE);
        assertThat(one.displayName()).isEqualTo("Synthetic form one");
        assertThat(one.cascadeOrder()).isEqualTo(1);
        assertThat(one.appliesTo()).containsExactly(PersonType.INDIVIDUAL);
        assertThat(one.returnKinds()).containsExactly(new ReturnKind("SYN_ORIGINAL"));
        assertThat(one.positiveConditions()).containsExactly(new IsResident());
        assertThat(one.bars()).extracting(ItrFormBar::condition)
                .containsExactly(new Declared(new DeclarationCode("SYNTHETIC_YES_NO")));
        assertThat(one.furnishing()).extracting(option -> option.manner())
                .containsExactly(new FurnishingManner("SYN_ELECTRONIC"));
        ItrFormSpec two = forms.get(TWO);
        assertThat(two.appliesTo()).containsExactlyInAnyOrder(PersonType.INDIVIDUAL, PersonType.HUF);
        assertThat(two.excludes()).containsExactly(ONE);
        assertThat(two.bars()).isEmpty();
    }

    @Test
    void load_whenTheSecondYearIsLoaded_keepsTheFirstVersionsBar() {
        // Given / When / Then
        assertThat(PACKS.load(TaxYearId.of(2100)).itrForms().get(ONE).bars()).extracting(ItrFormBar::condition)
                .containsExactly(new Declared(new DeclarationCode("SYNTHETIC_YES_NO")));
    }

    @Test
    void load_whenTheLaterVersionsYearComes_carriesItsBar() {
        // Given / When
        var forms = PACKS.load(TaxYearId.of(2101)).itrForms();

        // Then
        assertThat(forms).containsOnlyKeys(ONE, TWO);
        assertThat(forms.get(ONE).bars()).extracting(ItrFormBar::condition).containsExactly(
                new DeclaredAmountAbove(new DeclarationCode("SYNTHETIC_AMOUNT"), Money.of("1000")));
    }

    @Test
    void read_whenTheSyntheticTimelineIsRead_holdsEachFormsSource() {
        // Given / When
        Timeline timeline = TimelineReader.read(Path.of("src/test/resources/rulepacks-synthetic/timeline"));

        // Then
        assertThat(timeline.itrFormSources()).containsOnlyKeys(ONE, TWO);
        assertThat(timeline.itrFormSources().get(ONE)).contains("\"id\": \"SYN-1\"");
    }

    @Test
    void read_whenTheShippedTimelineIsRead_holdsItr1AndItr2() {
        // Given / When
        Timeline timeline = TimelineReader.read(Path.of("src/main/resources/rulepacks/timeline"));

        // Then — r.164(2) and (4)'s forms, the first shipped (W-090c2)
        assertThat(timeline.itrFormSources()).containsOnlyKeys(new ItrFormCode("ITR-1"), new ItrFormCode("ITR-2"));
    }
}
