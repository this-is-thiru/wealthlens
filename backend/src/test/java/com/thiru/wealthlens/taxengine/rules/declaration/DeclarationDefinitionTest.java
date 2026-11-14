package com.thiru.wealthlens.taxengine.rules.declaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A declaration refuses a row that could not be asked: no code, no question, or no way to answer it, no years it is
 * asked in, or no source (W-090a1; proven in W-265, D-531).
 */
class DeclarationDefinitionTest {

    private static final DeclarationCode CODE = new DeclarationCode("SYNTHETIC_YES_NO");
    private static final DateRange ONE_YEAR = new DateRange(LocalDate.of(2026, 4, 1), Optional.of(LocalDate.of(2027, 3, 31)));
    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-265"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: synthetic");

    @Test
    void constructor_whenEveryPartIsGiven_keepsThem() {
        // Given / When
        DeclarationDefinition definition = new DeclarationDefinition(CODE, "Asked?", AnswerKind.YES_NO, ONE_YEAR, SOURCE);

        // Then
        assertThat(definition.code()).isEqualTo(CODE);
        assertThat(definition.answerKind()).isEqualTo(AnswerKind.YES_NO);
    }

    @Test
    void constructor_whenTheCodeIsMissing_refuses() {
        assertThatThrownBy(() -> new DeclarationDefinition(null, "Asked?", AnswerKind.YES_NO, ONE_YEAR, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A declaration definition must have a code");
    }

    @Test
    void constructor_whenTheQuestionIsMissingOrBlank_refuses() {
        assertThatThrownBy(() -> new DeclarationDefinition(CODE, null, AnswerKind.YES_NO, ONE_YEAR, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SYNTHETIC_YES_NO must state its question");
        assertThatThrownBy(() -> new DeclarationDefinition(CODE, " ", AnswerKind.YES_NO, ONE_YEAR, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SYNTHETIC_YES_NO must state its question");
    }

    @Test
    void constructor_whenTheAnswerKindValidityOrSourceIsMissing_refuses() {
        String message = "SYNTHETIC_YES_NO must say how it is answered, when it is asked and its source";
        assertThatThrownBy(() -> new DeclarationDefinition(CODE, "Asked?", null, ONE_YEAR, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        assertThatThrownBy(() -> new DeclarationDefinition(CODE, "Asked?", AnswerKind.YES_NO, null, SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        assertThatThrownBy(() -> new DeclarationDefinition(CODE, "Asked?", AnswerKind.YES_NO, ONE_YEAR, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }
}
