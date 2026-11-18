package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * W-131j (C2a). On Linux, a child run as this JVM's user reads the JVM's /proc environ before the call and is denied after it. It
 * leaves this test JVM non-dumpable for the rest of its run, which no other test depends on. Linux only: the pipeline runs it.
 */
class ProcessProtectionTest {

    private static final DocumentSettings.Isolation ISOLATION = new DocumentSettings.Isolation(256, 30, Duration.ofSeconds(10), 16_777_216);

    private static String whatTheChildSees() {
        return new IsolatedPdfRead(ISOLATION, Duration.ofSeconds(20), IsolatedPdfRead.launching(ParentEnvironPdfReadChild.class, ISOLATION))
                .words(new DocumentContent(TestPdfs.pages(1))).reason();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void makeNonDumpable_whenOnLinux_closesTheEnvironmentToAChildOfTheSameUser() {
        // Given — the control: before the call, the same-user child reads the environment
        assertThat(whatTheChildSees()).isEqualTo("READ");

        // When
        ProcessProtection.makeNonDumpable();

        // Then
        assertThat(whatTheChildSees()).isEqualTo("DENIED");
    }

    @Test
    @DisabledOnOs(OS.LINUX)
    void makeNonDumpable_whenNotOnLinux_skipsWithoutFailing() {
        // When / Then — no /proc to protect; the call is skipped, and the start goes on
        assertThatCode(ProcessProtection::makeNonDumpable).doesNotThrowAnyException();
    }
}
