package com.thiru.wealthlens.taxplanning.policy.service;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceCatalogueRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceLimitRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.PerquisitePolicyRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.TaxSlabPolicyRepository;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Log4j2
@RequiredArgsConstructor
public class PolicySeederService {

    private final TaxSlabPolicyRepository slabRepo;
    private final PerquisitePolicyRepository perquisiteRepo;
    private final AllowanceCatalogueRepository allowanceRepo;
    private final AllowanceLimitRepository limitRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void seed() {
        log.info("Starting tax policy seeding for 2025-26");
        seedSlabPolicies();
        seedPerquisitePolicy();
        seedAllowanceCatalogue();
        seedAllowanceLimits();
        log.info("Tax policy seeding completed");
    }

    private void seedSlabPolicies() {
        seedSlabPolicy("data/tax-policies/slab-policy-2025-26-new.json", RegimeType.NEW_REGIME);
        seedSlabPolicy("data/tax-policies/slab-policy-2025-26-old.json", RegimeType.OLD_REGIME);
    }

    private void seedSlabPolicy(String path, RegimeType regimeType) {
        try {
            String taxYear = "2025-26";
            Optional<TaxSlabPolicyEntity> existing = slabRepo.findByTaxYearAndRegimeTypeAndStatus(
                    taxYear, regimeType, EntityStatus.ACTIVE);
            if (existing.isPresent()) {
                log.info("ACTIVE {} slab policy already exists for {}, skipping", regimeType, taxYear);
                return;
            }

            String json = readResource(path);
            TaxSlabPolicyEntity entity = objectMapper.readValue(json, TaxSlabPolicyEntity.class);
            slabRepo.save(entity);
            log.info("Seeded {} slab policy for {}", regimeType, taxYear);
        } catch (Exception e) {
            log.error("Failed to seed slab policy from {}: {}", path, e.getMessage());
        }
    }

    private void seedPerquisitePolicy() {
        try {
            String taxYear = "2025-26";
            Optional<PerquisitePolicyEntity> existing = perquisiteRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE);
            if (existing.isPresent()) {
                log.info("ACTIVE perquisite policy already exists for {}, skipping", taxYear);
                return;
            }

            String json = readResource("data/tax-policies/perquisite-policy-2025-26.json");
            PerquisitePolicyEntity entity = objectMapper.readValue(json, PerquisitePolicyEntity.class);
            perquisiteRepo.save(entity);
            log.info("Seeded perquisite policy for {}", taxYear);
        } catch (Exception e) {
            log.error("Failed to seed perquisite policy: {}", e.getMessage());
        }
    }

    private void seedAllowanceCatalogue() {
        try {
            String taxYear = "2025-26";
            List<AllowanceCatalogueEntity> existing = allowanceRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE);
            if (!existing.isEmpty()) {
                log.info("ACTIVE allowance catalogue entries already exist for {}, skipping", taxYear);
                return;
            }

            String json = readResource("data/tax-policies/allowance-catalogue-2025-26.json");
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.get("items");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    AllowanceCatalogueEntity entity = objectMapper.treeToValue(item, AllowanceCatalogueEntity.class);
                    if (entity.getStatus() == null) {
                        entity.setStatus(EntityStatus.ACTIVE);
                    }
                    allowanceRepo.save(entity);
                }
                log.info("Seeded {} allowance catalogue entries for {}", items.size(), taxYear);
            }
        } catch (Exception e) {
            log.error("Failed to seed allowance catalogue: {}", e.getMessage());
        }
    }

    private void seedAllowanceLimits() {
        try {
            String taxYear = "2025-26";
            List<AllowanceLimitEntity> existing = limitRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE);
            if (!existing.isEmpty()) {
                log.info("ACTIVE allowance limits already exist for {}, skipping", taxYear);
                return;
            }

            String json = readResource("data/tax-policies/allowance-limits-2025-26.json");
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    AllowanceLimitEntity entity = objectMapper.treeToValue(item, AllowanceLimitEntity.class);
                    limitRepo.save(entity);
                }
                log.info("Seeded {} allowance limits for {}", root.size(), taxYear);
            }
        } catch (Exception e) {
            log.error("Failed to seed allowance limits: {}", e.getMessage());
        }
    }

    private String readResource(String path) throws Exception {
        Resource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }
}
