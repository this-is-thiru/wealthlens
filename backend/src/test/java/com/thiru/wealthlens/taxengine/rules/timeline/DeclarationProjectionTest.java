package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.declaration.AnswerKind;
import com.thiru.wealthlens.taxengine.rules.declaration.DeclarationDefinition;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The declaration catalogue as each year sees it (W-090a1), on the synthetic pack: a row read from {@code
 * exec/declarations/}, compiled into the year's {@code exec/declarations.json} where its validity overlaps the year,
 * and assembled into the pack. {@code SYNTHETIC_YES_NO} and {@code SYNTHETIC_AMOUNT} are asked from 2099-04-01;
 * {@code SYNTHETIC_LATER} only from 2101-04-01, the first day of 2101-02.
 */
class DeclarationProjectionTest {

    private static final String ROOT = "rulepacks-synthetic";
    private static final ShippedRulePacks PACKS =
            ShippedRulePacks.compile(ROOT, ClasspathRulePackProvider.shippedYears(ROOT));
    private static final DeclarationCode YES_NO = new DeclarationCode("SYNTHETIC_YES_NO");
    private static final DeclarationCode AMOUNT = new DeclarationCode("SYNTHETIC_AMOUNT");
    private static final DeclarationCode LATER = new DeclarationCode("SYNTHETIC_LATER");

    @Test
    void load_whenTheRowsAreAskedFromTheFirstYear_carriesEachWithItsKindAndQuestion() {
        // Given / When
        var catalogue = PACKS.load(TaxYearId.of(2099)).declarations();

        // Then
        assertThat(catalogue).containsOnlyKeys(YES_NO, AMOUNT);
        DeclarationDefinition director = catalogue.get(YES_NO);
        assertThat(director.answerKind()).isEqualTo(AnswerKind.YES_NO);
        assertThat(director.question())
                .isEqualTo("Were you a director in a company at any time during the tax year?");
        assertThat(director.validity().from()).isEqualTo(LocalDate.of(2099, 4, 1));
        assertThat(catalogue.get(AMOUNT).answerKind()).isEqualTo(AnswerKind.AMOUNT);
    }

    @Test
    void load_whenARowIsAskedOnlyFromALaterYear_leavesItOutOfTheEarlierYears() {
        // Given / When / Then
        assertThat(PACKS.load(TaxYearId.of(2100)).declarations()).containsOnlyKeys(YES_NO, AMOUNT);
    }

    @Test
    void load_whenTheLaterRowsYearComes_carriesIt() {
        // Given / When / Then
        assertThat(PACKS.load(TaxYearId.of(2101)).declarations().keySet()).isEqualTo(Set.of(YES_NO, AMOUNT, LATER));
    }

    @Test
    void read_whenTheSyntheticTimelineIsRead_holdsEveryRowWithItsSource() {
        // Given / When
        Timeline timeline = TimelineReader.read(Path.of("src/test/resources/rulepacks-synthetic/timeline"));

        // Then
        assertThat(timeline.declarations()).containsOnlyKeys(YES_NO, AMOUNT, LATER);
        assertThat(timeline.declarationSources().get(LATER)).contains("\"code\": \"SYNTHETIC_LATER\"");
    }
}
