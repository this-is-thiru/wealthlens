package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** How a printed word is compared with a label's word (W-126b, W-126e2). */
class LabelMatchTest {

    @Test
    void normal_whenTheWordCarriesTheUnicodeHyphen_isTheAsciiHyphen() {
        // Given — Form 16's "Chapter VI-A" rows carry U+2010 in the Gazette's text layer (W-126e1)
        String gazette = "VI" + (char) 0x2010 + "A";

        // When / Then
        assertThat(LabelMatch.normal(gazette)).isEqualTo("VI-A");
    }
}
