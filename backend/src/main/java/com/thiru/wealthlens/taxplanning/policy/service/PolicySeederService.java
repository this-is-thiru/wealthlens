package com.thiru.wealthlens.taxplanning.policy.service;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.dto.PolicySeedReport;
import com.thiru.wealthlens.taxplanning.policy.dto.PolicySeedReport.Part;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceCatalogueRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceLimitRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.PerquisitePolicyRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.TaxSlabPolicyRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seeds the TY 2025-26 tax policies — slabs, perquisites, the allowance catalogue and limits — from
 * {@code data/tax-policies/}, when a {@code SUPER_USER} asks ({@code POST /tax-planning/admin/policies/seed}).
 *
 * <p>An operation, not a startup hook (D-280): at boot it wrote as nobody, in an order nobody chose, and a file that
 * would not load was only logged. Now the caller is recorded on every document by the auditing, a dry run comes first,
 * and a file that fails is an error naming it — the files before it stay seeded.
 *
 * <p>Idempotent per document, not per file (D-282): a document is skipped only when one with its key is already on
 * file, so a run after one that failed part-way seeds what is missing and a run after a complete one writes nothing.
 * A document an operator changed or deactivated is never overwritten.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class PolicySeederService {

    private static final String TAX_YEAR = "2025-26";
    private static final String SLAB_NEW_FILE = "data/tax-policies/slab-policy-2025-26-new.json";
    private static final String SLAB_OLD_FILE = "data/tax-policies/slab-policy-2025-26-old.json";
    private static final String PERQUISITE_FILE = "data/tax-policies/perquisite-policy-2025-26.json";
    private static final String CATALOGUE_FILE = "data/tax-policies/allowance-catalogue-2025-26.json";
    private static final String LIMITS_FILE = "data/tax-policies/allowance-limits-2025-26.json";

    private final TaxSlabPolicyRepository slabRepo;
    private final PerquisitePolicyRepository perquisiteRepo;
    private final AllowanceCatalogueRepository allowanceRepo;
    private final AllowanceLimitRepository limitRepo;

    /**
     * An unknown property is refused, the opposite of Jackson 3's default: a misspelt key would
     * otherwise vanish and the policy would seed with the field unset and nothing to say so (W-203).
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Seed what is missing, unless {@code dryRun}; {@code auditor} is who asked. */
    public PolicySeedReport seed(boolean dryRun, String auditor) {
        List<Part> parts = List.of(
                slab(SLAB_NEW_FILE, RegimeType.NEW_REGIME, dryRun),
                slab(SLAB_OLD_FILE, RegimeType.OLD_REGIME, dryRun),
                perquisite(dryRun),
                catalogue(dryRun),
                limits(dryRun));
        log.info("Tax policy seed (dryRun={}, by {}): {}", dryRun, auditor, parts);
        return new PolicySeedReport(dryRun, auditor, parts);
    }

    private Part slab(String file, RegimeType regime, boolean dryRun) {
        String name = "SLAB_" + regime;
        if (slabRepo.findByTaxYearAndRegimeTypeAndStatus(TAX_YEAR, regime, EntityStatus.ACTIVE).isPresent()) {
            return new Part(name, file, 0, 1);
        }
        TaxSlabPolicyEntity policy = read(file, new TaxSlabPolicyEntity());
        if (!dryRun) {
            slabRepo.save(policy);
        }
        return new Part(name, file, 1, 0);
    }

    private Part perquisite(boolean dryRun) {
        if (perquisiteRepo.findByTaxYearAndStatus(TAX_YEAR, EntityStatus.ACTIVE).isPresent()) {
            return new Part("PERQUISITE", PERQUISITE_FILE, 0, 1);
        }
        PerquisitePolicyEntity policy = read(PERQUISITE_FILE, new PerquisitePolicyEntity());
        if (!dryRun) {
            perquisiteRepo.save(policy);
        }
        return new Part("PERQUISITE", PERQUISITE_FILE, 1, 0);
    }

    /** Keyed by code, tax year and regime. */
    private Part catalogue(boolean dryRun) {
        int added = 0;
        int present = 0;
        for (JsonNode item : array(CATALOGUE_FILE, tree(CATALOGUE_FILE).get("items"))) {
            AllowanceCatalogueEntity entry = convert(CATALOGUE_FILE, item, new AllowanceCatalogueEntity());
            if (entry.getStatus() == null) {
                entry.setStatus(EntityStatus.ACTIVE);
            }
            if (allowanceRepo.existsByCodeAndTaxYearAndRegimeType(entry.getCode(), entry.getTaxYear(),
                    entry.getRegimeType())) {
                present++;
                continue;
            }
            if (!dryRun) {
                allowanceRepo.save(entry);
            }
            added++;
        }
        return new Part("ALLOWANCE_CATALOGUE", CATALOGUE_FILE, added, present);
    }

    /** Keyed by allowance code, tax year, regime and employer type. */
    private Part limits(boolean dryRun) {
        int added = 0;
        int present = 0;
        for (JsonNode item : array(LIMITS_FILE, tree(LIMITS_FILE))) {
            AllowanceLimitEntity limit = convert(LIMITS_FILE, item, new AllowanceLimitEntity());
            if (limitRepo.existsByAllowanceCodeAndTaxYearAndRegimeTypeAndEmployerType(limit.getAllowanceCode(),
                    limit.getTaxYear(), limit.getRegimeType(), limit.getEmployerType())) {
                present++;
                continue;
            }
            if (!dryRun) {
                limitRepo.save(limit);
            }
            added++;
        }
        return new Part("ALLOWANCE_LIMITS", LIMITS_FILE, added, present);
    }

    /**
     * Fills {@code fresh}, built by its no-argument constructor, rather than letting Jackson pick a constructor: Jackson 3
     * chooses the entities' all-arguments one and passes null for the audit metadata no file carries, and a document
     * saved without it records no author (found in W-088u8a; every policy the boot seeder wrote lacks it). Package-private
     * so a test can hand it a fixture.
     */
    <T> T read(String file, T fresh) {
        try {
            return objectMapper.readerForUpdating(fresh).readValue(readResource(file));
        } catch (IOException | JacksonException e) {
            throw failed(file, e);
        }
    }

    private JsonNode tree(String file) {
        try {
            return objectMapper.readTree(readResource(file));
        } catch (IOException | JacksonException e) {
            throw failed(file, e);
        }
    }

    /** As {@link #read}, for one element of a file's array. */
    private <T> T convert(String file, JsonNode node, T fresh) {
        try {
            return objectMapper.readerForUpdating(fresh).readValue(node);
        } catch (JacksonException e) {
            throw failed(file, e);
        }
    }

    private static JsonNode array(String file, JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalStateException(file + ": expected an array of policy documents — the files before it are"
                    + " seeded; fix the file and seed again");
        }
        return node;
    }

    private static IllegalStateException failed(String file, Throwable cause) {
        return new IllegalStateException(file + ": " + cause.getMessage() + " — the files before it are seeded; fix the"
                + " file and seed again", cause);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
