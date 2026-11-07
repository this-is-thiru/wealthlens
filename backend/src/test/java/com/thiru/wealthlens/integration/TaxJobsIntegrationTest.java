package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.jobs.AutoArchive;
import com.thiru.wealthlens.taxplanning.jobs.JobContext;
import com.thiru.wealthlens.taxplanning.jobs.JobFailure;
import com.thiru.wealthlens.taxplanning.jobs.JobFailureRepository;
import com.thiru.wealthlens.taxplanning.jobs.JobLease;
import com.thiru.wealthlens.taxplanning.jobs.RetentionPurge;
import com.thiru.wealthlens.taxplanning.jobs.StalenessSweep;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.DraftStaleness;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Auto-archive and retention purge over Mongo (W-137, W-136; D-449), on a clock the test sets. Every expected date is worked by
 * hand in {@code plans/W-137.md}: 18 months before 2026-11-06 is 2025-05-06; 12 months before it is 2025-11-06; tax year 2025-26
 * ends 31-3-2026, and its eight succeeding years end 31-3-2034.
 */
class TaxJobsIntegrationTest extends AbstractIntegrationTest {

    /** A clock each test sets, so a run can be placed on either side of a boundary. */
    static final class SettableClock extends Clock {

        private Instant instant = Instant.parse("2026-11-06T06:30:00Z");

        void set(String isoInstant) {
            instant = Instant.parse(isoInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class SetDay {

        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
        }
    }

    @Autowired
    private SettableClock clock;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private AutoArchive autoArchive;

    @Autowired
    private RetentionPurge retentionPurge;

    @Autowired
    private JobFailureRepository failures;

    @Autowired
    private StalenessSweep stalenessSweep;

    @Autowired
    private TaxCalculator taxCalculator;

    @BeforeEach
    void theDayOfTheHandFigures() {
        clock.set("2026-11-06T06:30:00Z");
    }

    private void workspace(String id, String primaryDraftId) {
        TaxYearWorkspace workspace = new TaxYearWorkspace();
        workspace.setId(id);
        workspace.setProfileId("profile-1");
        workspace.setTaxYearStart(2025);
        workspace.setPrimaryDraftId(primaryDraftId);
        mongoTemplate.save(workspace);
    }

    private void draft(String id, DraftStatus status, int taxYear, String lastChanged) {
        FilingDraft draft = new FilingDraft();
        draft.setId(id);
        draft.setWorkspaceId("workspace-1");
        draft.setProfileId("profile-1");
        draft.setTaxYearStart(taxYear);
        draft.setLabel(id);
        draft.setStatus(status);
        draft.setInputsJson("{}");
        mongoTemplate.save(draft);
        set(id, "audit_metadata.updatedAt", LocalDateTime.parse(lastChanged));
    }

    private void set(String draftId, String field, LocalDateTime value) {
        mongoTemplate.updateFirst(query(where("_id").is(draftId)), new Update().set(field, value), FilingDraft.class);
    }

    private FilingDraft stored(String id) {
        return mongoTemplate.findById(id, FilingDraft.class);
    }

    @Test
    void autoArchive_whenADraftWasUntouchedForEighteenMonths_archivesItButNotThePrimaryOrAReadyOne() {
        // Given — run on 2026-11-06T06:30Z; the cutoff is 2025-05-06T06:30
        workspace("workspace-1", "primary");
        draft("old", DraftStatus.DRAFT, 2025, "2025-05-05T12:00:00");
        draft("recent", DraftStatus.DRAFT, 2025, "2025-05-07T12:00:00");
        draft("primary", DraftStatus.DRAFT, 2025, "2025-05-05T12:00:00");
        draft("ready", DraftStatus.READY, 2025, "2025-05-05T12:00:00");

        // When
        boolean ran = runner.run(autoArchive);

        // Then
        assertThat(ran).isTrue();
        assertThat(stored("old").getStatus()).isEqualTo(DraftStatus.ARCHIVED);
        assertThat(stored("old").getArchivedAt()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
        assertThat(stored("recent").getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(stored("primary").getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(stored("ready").getStatus()).isEqualTo(DraftStatus.READY);
        assertThat(mongoTemplate.findAll(JobLease.class)).isEmpty();
    }

    @Test
    void retentionPurge_whenAnArchivedDraftWasNeverActedOn_deletesItAndItsOwnRecordsAfterTwelveMonths() {
        // Given — run on 2026-11-06T06:30Z; the cutoff is 2025-11-06T06:30
        workspace("workspace-1", "expired");
        draft("expired", DraftStatus.ARCHIVED, 2024, "2025-11-05T12:00:00");
        set("expired", "archived_at", LocalDateTime.parse("2025-11-05T12:00:00"));
        draft("young", DraftStatus.ARCHIVED, 2024, "2025-11-07T12:00:00");
        set("young", "archived_at", LocalDateTime.parse("2025-11-07T12:00:00"));
        DraftRevision revision = new DraftRevision();
        revision.setId("revision-1");
        revision.setDraftId("expired");
        mongoTemplate.save(revision);
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-1");
        computation.setDraftId("expired");
        computation.setResultJson("{}");
        mongoTemplate.save(computation);

        // When
        runner.run(retentionPurge);

        // Then
        assertThat(stored("expired")).isNull();
        assertThat(mongoTemplate.findAll(DraftRevision.class)).isEmpty();
        assertThat(mongoTemplate.findAll(DraftComputation.class)).isEmpty();
        assertThat(mongoTemplate.findById("workspace-1", TaxYearWorkspace.class).getPrimaryDraftId()).isNull();
        assertThat(stored("young")).isNotNull();
    }

    @Test
    void retentionPurge_whenADraftWasActedOn_keepsItThroughTheLastYearItsLossCouldBeCarriedInto() {
        // Given — tax year 2025-26, acted on 2026-05-01; one later archived by the user on 2026-05-02
        workspace("workspace-1", null);
        draft("filed", DraftStatus.ACTED_ON, 2025, "2026-05-01T12:00:00");
        set("filed", "acted_on_at", LocalDateTime.parse("2026-05-01T12:00:00"));
        draft("filed-then-archived", DraftStatus.ARCHIVED, 2025, "2026-05-02T12:00:00");
        set("filed-then-archived", "acted_on_at", LocalDateTime.parse("2026-05-01T12:00:00"));
        set("filed-then-archived", "archived_at", LocalDateTime.parse("2026-05-02T12:00:00"));

        // When — 2027-06-01: twelve months past the archiving, not eight years past the year
        clock.set("2027-06-01T06:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(stored("filed-then-archived")).isNotNull();

        // When — 31-3-2034, the last day of 2033-34, the eighth succeeding year
        clock.set("2034-03-31T12:00:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(stored("filed")).isNotNull();

        // When — the day after
        clock.set("2034-04-01T00:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(stored("filed")).isNull();
        assertThat(stored("filed-then-archived")).isNull();
        assertThat(failures.findAll()).isEmpty();
    }

    @Test
    void retentionPurge_whenNoPackCoversTheYear_keepsTheDraftAndRecordsWhy() {
        // Given — the application ships 2025-26 and 2026-27 only
        workspace("workspace-1", null);
        draft("old-year", DraftStatus.ACTED_ON, 2019, "2020-07-01T12:00:00");
        set("old-year", "acted_on_at", LocalDateTime.parse("2020-07-01T12:00:00"));

        // When
        runner.run(retentionPurge);

        // Then
        assertThat(stored("old-year")).isNotNull();
        assertThat(failures.findAll()).singleElement().satisfies(failure -> {
            assertThat(failure.getJob()).isEqualTo("retention-purge");
            assertThat(failure.getSubject()).isEqualTo("tax year 2019-20");
        });
    }

    @Test
    void run_whenAnotherRunHoldsTheLease_doesNothingUntilItExpires() {
        // Given
        workspace("workspace-1", null);
        draft("old", DraftStatus.DRAFT, 2025, "2025-05-05T12:00:00");
        JobLease held = new JobLease();
        held.setJob("auto-archive");
        held.setHolder("another-instance");
        held.setExpiresAt(Instant.parse("2026-11-06T06:40:00Z"));
        mongoTemplate.save(held);

        // When
        boolean whileHeld = runner.run(autoArchive);
        clock.set("2026-11-06T06:41:00Z");
        boolean afterExpiry = runner.run(autoArchive);

        // Then
        assertThat(whileHeld).isFalse();
        assertThat(afterExpiry).isTrue();
        assertThat(stored("old").getStatus()).isEqualTo(DraftStatus.ARCHIVED);
    }

    @Test
    void retentionPurge_whenTheLeaseIsLostBeforeADelete_deletesNothing() {
        // Given
        workspace("workspace-1", null);
        draft("expired", DraftStatus.ARCHIVED, 2024, "2025-01-01T12:00:00");
        set("expired", "archived_at", LocalDateTime.parse("2025-01-01T12:00:00"));

        // When — a run whose lease another has taken over
        retentionPurge.run(new JobContext("retention-purge", clock, () -> false, failures));

        // Then
        assertThat(stored("expired")).isNotNull();
        assertThat(mongoTemplate.findAll(JobFailure.class)).isEmpty();
    }

    /** The shipped 2026-27 pack's law and exec versions, with one of them replaced by an older one. */
    private List<String> withOlder(String layer) {
        return taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow().stream()
                .map(version -> version.contains("layer=" + layer) ? version.replaceAll("version=[^,]+", "version=2026-27.old.1") : version)
                .sorted().toList();
    }

    private void computation(String draftId, List<String> lawAndExec, Map<String, String> ruleChecksums) {
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-" + draftId);
        computation.setReceipt("receipt-" + draftId);
        computation.setDraftId(draftId);
        computation.setRevision(1);
        computation.setResultJson("{}");
        computation.setLawAndExec(lawAndExec);
        computation.setRuleChecksums(ruleChecksums);
        mongoTemplate.save(computation);
    }

    private Map<String, String> pinnedWithOneChanged() {
        String unchanged = taxCalculator.ruleChecksums(TaxYearId.of(2026), Set.of("ADV-LIABILITY")).get("ADV-LIABILITY");
        return Map.of("ADV-INSTALMENTS", "sha256:0", "ADV-LIABILITY", unchanged);
    }

    @Test
    void stalenessSweep_whenTheLawMovedUnderAReadyDraft_flagsItWithTheRulesItReadThatChanged() {
        // Given — computed under an older law layer; of its two rules, ADV-INSTALMENTS has changed since
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("ready", withOlder("LAW"), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then — R-8: flagged, never recomputed
        DraftStaleness staleness = stored("ready").getStaleness();
        assertThat(staleness.changedRules()).containsExactly("ADV-INSTALMENTS");
        assertThat(staleness.ruleDiffAvailable()).isTrue();
        assertThat(staleness.receipt()).isEqualTo("receipt-ready");
        assertThat(staleness.computedUnder()).isEqualTo(withOlder("LAW"));
        assertThat(staleness.current()).isEqualTo(taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow());
        assertThat(staleness.since()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
        assertThat(mongoTemplate.findAll(DraftComputation.class)).hasSize(1);
    }

    @Test
    void stalenessSweep_whenOnlyTheExecLayerMoved_flagsAFiledDraftToo() {
        // Given — V9: any change to a computed year, not only to the law layer
        workspace("workspace-1", null);
        draft("filed", DraftStatus.ACTED_ON, 2026, "2026-10-01T12:00:00");
        computation("filed", withOlder("EXEC"), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("filed").getStaleness()).isNotNull();
        assertThat(stored("filed").getStatus()).isEqualTo(DraftStatus.ACTED_ON);
    }

    @Test
    void stalenessSweep_whenADraftIsBeingWorkedOrItsLawIsCurrent_leavesItAlone() {
        // Given
        workspace("workspace-1", null);
        draft("working", DraftStatus.DRAFT, 2026, "2026-10-01T12:00:00");
        computation("working", withOlder("LAW"), pinnedWithOneChanged());
        draft("current", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("current", taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow(), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("working").getStaleness()).isNull();
        assertThat(stored("current").getStaleness()).isNull();
    }

    @Test
    void stalenessSweep_whenRunAgainLater_keepsTheFirstFlag() {
        // Given
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("ready", withOlder("LAW"), pinnedWithOneChanged());
        runner.run(stalenessSweep);

        // When
        clock.set("2026-11-07T06:30:00Z");
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("ready").getStaleness().since()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
    }

    @Test
    void stalenessSweep_whenTheComputationPredatesItsPins_readsTheVersionsFromItsResultAndNamesNoRules() {
        // Given — stored before W-138: the versions only inside the result, no rule checksums
        workspace("workspace-1", null);
        draft("old", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-old");
        computation.setReceipt("TXC-2026-BBBBBB");
        computation.setDraftId("old");
        computation.setRevision(1);
        computation.setResultJson("{\"tax\": {\"lawAndExec\": [\"" + String.join("\", \"", withOlder("LAW")) + "\"]}}");
        mongoTemplate.save(computation);

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("old").getStaleness().ruleDiffAvailable()).isFalse();
        assertThat(stored("old").getStaleness().changedRules()).isEmpty();
    }

    @Test
    void stalenessSweep_whenTheProfileIsHeldForErasure_flagsNothing() {
        // Given — a READY draft whose law moved, as in the flagging case, but its profile held for erasure (W-235)
        TaxpayerProfile held = new TaxpayerProfile();
        held.setId("profile-1");
        held.setErasureRequestedAt(LocalDateTime.parse("2026-11-01T00:00:00"));
        mongoTemplate.save(held);
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("ready", withOlder("LAW"), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("ready").getStaleness()).isNull();
    }
}
