package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.entity.HoldingPeriodPolicyEntity;
import com.thiru.wealthlens.portfolio.repository.HoldingPeriodPolicyRepository;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the shipped holding-period policies.
 *
 * <p>An explicit call, never a startup hook — the same rule ADR-27 sets for rate cards, and for the
 * same reasons: a deployment should not write reference data as a side effect of booting, and a
 * seeded row should record who asked for it.
 *
 * <p>Idempotent by {@code policyCode}, and a policy already on file is <b>left alone</b>. That is
 * deliberate rather than lazy: an operator's correction must survive a redeploy, and under ADR-26 a
 * rule change ships as a new policy with a new code rather than an edit to an existing one.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class HoldingPeriodSeeder {

    private static final String POLICY_FILE = "data/holding-periods/holding-period-policies.json";

    private final HoldingPeriodPolicyRepository repository;
    private final HoldingPeriodResolver resolver;

    /** @return the policy codes actually written; empty when everything was already on file */
    public List<String> seed(String auditor) {
        log.info("Seeding holding-period policies, requested by {}", auditor);

        List<String> created = new ArrayList<>();
        for (HoldingPeriodPolicyEntity policy : read()) {
            if (repository.findByPolicyCode(policy.getPolicyCode()).isPresent()) {
                continue;
            }
            repository.save(policy);
            created.add(policy.getPolicyCode());
            log.info("Seeded holding-period policy {}", policy.getPolicyCode());
        }

        // A resolver already holding the old set would keep classifying against it.
        resolver.evictAll();
        log.info("Holding-period seeding completed: {} policies written", created.size());
        return created;
    }

    private List<HoldingPeriodPolicyEntity> read() {
        try (InputStream stream = new ClassPathResource(POLICY_FILE).getInputStream()) {
            return TJsonMapper.readAsList(new String(stream.readAllBytes()), HoldingPeriodPolicyEntity.class);
        } catch (IOException e) {
            // Starting with no policies would classify every disposal as unclassified, which is
            // survivable but silent. Failing here makes it a deployment error instead.
            throw new IllegalStateException("Could not read " + POLICY_FILE, e);
        }
    }
}
