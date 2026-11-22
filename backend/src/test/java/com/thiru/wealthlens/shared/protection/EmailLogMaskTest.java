package com.thiru.wealthlens.shared.protection;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.joran.spi.JoranException;
import com.thiru.wealthlens.testsupport.ShippedLogLine;
import org.junit.jupiter.api.Test;

/**
 * The shipped {@code logback-spring.xml}: no email in a log line, from our own statements, a logged exception's message or a library
 * (W-358d1, D-873 C1a). A line's request id, not the person, ties it to the processing log's record of whom it concerned.
 */
class EmailLogMaskTest {

    @Test
    void write_whenAMessageNamesAnEmail_writesItMasked() throws JoranException {
        // When / Then — the shape of PortfolioQueryService:67's line
        assertThat(ShippedLogLine.written("Fetching portfolio stocks of owner.name+tag@mail.example.co.in", null))
                .contains("Fetching portfolio stocks of [email]").doesNotContain("owner.name", "mail.example.co.in");
    }

    @Test
    void write_whenAnExceptionNamesAnEmail_writesTheTraceOnceAndMasked() throws JoranException {
        // When — ControllerAdviser logs every refusal's message with its trace (AuthService:80's message)
        String line = ShippedLogLine.written("User with email a.b@c.io not found", new IllegalArgumentException("User with email a.b@c.io not found"));

        // Then
        assertThat(line).contains("User with email [email] not found").doesNotContain("a.b@c.io");
        assertThat(line.split("IllegalArgumentException", -1)).hasSize(2);
    }

    @Test
    void write_whenALineNamesAnEmailAndAPan_masksBoth() throws JoranException {
        // When / Then — the email mask wraps the PAN one, so neither undoes the other
        assertThat(ShippedLogLine.written("Saved trade outcome for email: anu@example.com, ABCPE1234F", null))
                .contains("email: [email], ABCXXXXX4F").doesNotContain("anu@example.com", "ABCPE1234F");
    }

    @Test
    void write_whenATokenOnlyLooksLikeAnEmail_leavesItAlone() throws JoranException {
        // When / Then — a host, a version with an at sign, and an address with no dotted domain are not emails
        assertThat(ShippedLogLine.written("cluster0.x6trgen.mongodb.net, v1.2@3, root@localhost", null))
                .contains("cluster0.x6trgen.mongodb.net", "v1.2@3", "root@localhost");
    }
}
