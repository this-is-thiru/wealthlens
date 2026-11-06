package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftStaleness;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Flags each ready or filed draft whose last computation ran under law and exec versions the shipped pack has since replaced, with
 * the rules its trace read that changed ({@code 13-edge-cases.md} R-8, {@code 18-temporal-versioning.md} V9; W-138, D-450). Never
 * recomputes. Idempotent: a draft already flagged against the same current versions is left alone.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class StalenessSweep implements TaxJob {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FilingDraftRepository drafts;
    private final DraftComputationRepository computations;
    private final TaxCalculator taxCalculator;

    @Override
    public String name() {
        return "staleness-sweep";
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(30);
    }

    @Override
    public void run(JobContext context) {
        int flagged = 0;
        for (FilingDraft draft : drafts.findByStatusIn(Set.of(DraftStatus.READY, DraftStatus.ACTED_ON))) {
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", name());
                return;
            }
            Optional<DraftComputation> latest = computations.findByDraftId(draft.getId()).stream()
                    .max(Comparator.comparingInt(DraftComputation::getRevision)
                            .thenComparing(computation -> computation.getAuditMetadata().getCreatedAt()));
            TaxYearId year = TaxYearId.of(draft.getTaxYearStart());
            Optional<List<String>> current = taxCalculator.packVersions(year);
            if (latest.isPresent() && current.isEmpty()) {
                context.refuse("draft " + draft.getId(), "no shipped pack for tax year " + year.label()
                        + ", so whether the law moved under it cannot be told");
            } else if (latest.isPresent() && flag(draft, latest.get(), current.get(), context)) {
                flagged++;
            }
        }
        log.info("Job {} flagged {} draft(s)", name(), flagged);
    }

    private boolean flag(FilingDraft draft, DraftComputation computation, List<String> current, JobContext context) {
        List<String> pinned = pinned(computation);
        if (pinned.equals(current) || draft.getStaleness() != null && draft.getStaleness().current().equals(current)) {
            return false;
        }
        Map<String, String> then = computation.getRuleChecksums();
        List<String> changed = then == null ? List.of() : changedRules(then, TaxYearId.of(draft.getTaxYearStart()));
        draft.setStaleness(new DraftStaleness(context.nowUtc(), computation.getReceipt(), pinned, current, changed, then != null));
        drafts.save(draft);
        log.info("Job {} flagged draft {}: computed under {}, now {}", name(), draft.getId(), pinned, current);
        return true;
    }

    /** The rules whose checksum now differs from {@code then}, or which the pack no longer holds. */
    private List<String> changedRules(Map<String, String> then, TaxYearId year) {
        Map<String, String> now = taxCalculator.ruleChecksums(year, then.keySet());
        return then.entrySet().stream().filter(rule -> !rule.getValue().equals(now.get(rule.getKey()))).map(Map.Entry::getKey)
                .sorted().toList();
    }

    /** What the computation ran under: its own field, or, for one stored before W-138, the copy inside its result. */
    private static List<String> pinned(DraftComputation computation) {
        if (computation.getLawAndExec() != null) {
            return computation.getLawAndExec();
        }
        List<String> versions = new ArrayList<>();
        MAPPER.readTree(computation.getResultJson()).at("/tax/lawAndExec").forEach(version -> versions.add(version.asString()));
        return versions;
    }
}
