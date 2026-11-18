package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IsolatedPdfReadTest {

    private static final DocumentSettings.Isolation ISOLATION = new DocumentSettings.Isolation(256, 30, Duration.ofSeconds(10), 16_777_216);
    private static final DocumentContent ANY = new DocumentContent(TestPdfs.pages(1));

    private static IsolatedPdfRead running(Class<?> child, DocumentSettings.Isolation isolation, Duration budget, String... properties) {
        List<String> launch = new ArrayList<>(IsolatedPdfRead.launching(child, isolation));
        // A test-only property goes before the main class, the command's last word, so the JVM reads it.
        launch.addAll(launch.size() - 1, List.of(properties));
        return new IsolatedPdfRead(isolation, budget, launch);
    }

    @Test
    void words_whenAPdfOfTwoPages_returnsItsPagesAndWordsFromTheChild() {
        // Given — TestPdfs prints "Page 1" and "Page 2", one page each
        IsolatedPdfRead reader = IsolatedPdfRead.of(ISOLATION, Duration.ofSeconds(30));

        // When
        PdfText text = reader.words(new DocumentContent(TestPdfs.pages(2)));

        // Then
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.READ);
        assertThat(text.pages()).isEqualTo(2);
        assertThat(text.words()).extracting(PrintedText.Word::text).containsExactly("Page", "1", "Page", "2");
    }

    @Test
    void pages_whenAPdfOfThreePages_countsThemWithoutWords() {
        // When
        PdfText text = IsolatedPdfRead.of(ISOLATION, Duration.ofSeconds(30)).pages(new DocumentContent(TestPdfs.pages(3)));

        // Then
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.READ);
        assertThat(text.pages()).isEqualTo(3);
        assertThat(text.words()).isEmpty();
    }

    @Test
    void pages_whenThePdfNeedsAPassword_saysSo() {
        // When
        PdfText text = IsolatedPdfRead.of(ISOLATION, Duration.ofSeconds(30)).pages(new DocumentContent(TestPdfs.locked()));

        // Then
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.PASSWORD);
    }

    @Test
    void pages_whenTheBytesAreNoPdf_isUnreadableWithIText_sReason() {
        // When
        PdfText text = IsolatedPdfRead.of(ISOLATION, Duration.ofSeconds(30))
                .pages(new DocumentContent("%PDF-1.4 not a pdf".getBytes(StandardCharsets.US_ASCII)));

        // Then
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.UNREADABLE);
        assertThat(text.reason()).isNotBlank();
    }

    @Test
    void words_whenTheChildNeverAnswers_isUnreadableAtTheBudgetAndTheChildIsGone(@TempDir Path directory) throws IOException,
            InterruptedException {
        // Given — a 2 s budget
        Path pidFile = directory.resolve("pid");
        IsolatedPdfRead reader = running(HangingPdfReadChild.class, ISOLATION, Duration.ofSeconds(2), "-Dw131f.pidfile=" + pidFile);

        // When
        long started = System.nanoTime();
        PdfText text = reader.words(ANY);
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        // Then — the reply says why, within the budget plus a second, and no process with the child's id is left alive
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.UNREADABLE);
        assertThat(text.reason()).isEqualTo("The PDF reader took longer than PT2S");
        assertThat(took).isLessThan(Duration.ofSeconds(3));
        long pid = Long.parseLong(Files.readString(pidFile));
        Thread.sleep(1_000);
        assertThat(ProcessHandle.of(pid).filter(ProcessHandle::isAlive)).isEmpty();
    }

    @Test
    void words_whenTheChildFloodsItsOutput_isUnreadableAtTheCap() {
        // Given — a cap of 1 MiB against an endless stream
        DocumentSettings.Isolation small = new DocumentSettings.Isolation(256, 30, Duration.ofSeconds(10), 1_048_576);

        // When
        PdfText text = running(FloodingPdfReadChild.class, small, Duration.ofSeconds(10)).words(ANY);

        // Then
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.UNREADABLE);
        assertThat(text.reason()).isEqualTo("The PDF reader's reply was larger than 1048576 bytes");
    }

    @Test
    void words_whenTheChildExitsWithAnError_isUnreadableNamingTheExit() {
        // When
        PdfText text = running(FailingPdfReadChild.class, ISOLATION, Duration.ofSeconds(10)).words(ANY);

        // Then
        assertThat(text.outcome()).isEqualTo(PdfText.Outcome.UNREADABLE);
        assertThat(text.reason()).isEqualTo("The PDF reader stopped (exit 3)");
    }

    @Test
    void read_whenTheReplyIsNotJsonOrHasAnUnknownField_isUnreadable() {
        // Given — the child answers PAGES with "{not json" and WORDS with an extra field
        IsolatedPdfRead reader = running(GarbagePdfReadChild.class, ISOLATION, Duration.ofSeconds(10));

        // When / Then
        assertThat(reader.pages(ANY).reason()).isEqualTo("The PDF reader's reply was not understood");
        assertThat(reader.words(ANY).reason()).isEqualTo("The PDF reader's reply was not understood");
    }

    @Test
    void words_whenTheChildSpinsPastItsCpuLimit_isStoppedBeforeTheWallClock() {
        // Given — 1 s of CPU against a 20 s wall clock
        DocumentSettings.Isolation oneSecond = new DocumentSettings.Isolation(256, 1, Duration.ofSeconds(10), 16_777_216);

        // When
        long started = System.nanoTime();
        PdfText text = running(SpinningPdfReadChild.class, oneSecond, Duration.ofSeconds(20)).words(ANY);
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        // Then
        assertThat(text.reason()).startsWith("The PDF reader stopped (exit ");
        assertThat(took).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void words_whenTheApplicationHasAnEnvironment_givesTheChildNoneOfIt() {
        // Given — the test JVM's environment is not empty (PATH at least), so a child that inherited it would name something
        assertThat(System.getenv()).isNotEmpty();

        // When
        PdfText text = running(EnvironmentPdfReadChild.class, ISOLATION, Duration.ofSeconds(20)).words(ANY);

        // Then — only what /bin/sh sets itself (PWD, SHLVL, _) and macOS adds to every process (__CF_USER_TEXT_ENCODING)
        assertThat(text.reason().isEmpty() ? List.<String>of() : List.of(text.reason().split(",")))
                .isSubsetOf("PWD", "SHLVL", "_", "__CF_USER_TEXT_ENCODING");
    }

    @Test
    void launching_whenTheApplicationRunsAsRoot_handsTheChildToNobody() {
        // When
        List<String> asRoot = IsolatedPdfRead.launching(PdfReadChild.class, ISOLATION, "root");
        List<String> asSomeoneElse = IsolatedPdfRead.launching(PdfReadChild.class, ISOLATION, "app");

        // Then
        assertThat(asRoot.subList(0, 7)).containsExactly("/bin/su", "-s", "/bin/sh", "-c", "ulimit -t 30 && exec \"$0\" \"$@\"", "--", "nobody");
        assertThat(asSomeoneElse.subList(0, 3)).containsExactly("/bin/sh", "-c", "ulimit -t 30 && exec \"$0\" \"$@\"");
    }

    @Test
    void words_whenTheChildReachesForTheNetwork_findsNoNameAndNoRoute() {
        // When
        PdfText text = running(NetworkPdfReadChild.class, ISOLATION, Duration.ofSeconds(20)).words(ANY);

        // Then — no name resolves, and a socket goes to the dead proxy, refused at once rather than timing out
        assertThat(text.reason()).startsWith("lookup=UnknownHostException socket=refused: ");
    }
}
