package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Reads the registry of charge codes a rate card may name.
 *
 * <p>Thin by design, and separate from {@link ChargeScheduleService} because the catalogue is its
 * own thing: schedules come and go per broker and period, while a charge code is a fact about
 * Indian market structure that outlives all of them. The seeder writes it; nothing else does.
 *
 * <p>Retired codes are withheld from the listing but remain resolvable, because historical charge
 * rows still carry them. A catalogue that forgot a code would leave those rows unexplainable.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeCatalogueService {

    private final ChargeCatalogueRepository chargeCatalogueRepository;

    /** Sorted by code: Mongo returns insertion order, which the seeder changes between runs. */
    public List<ChargeCatalogueEntity> findActive() {
        return chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(ChargeCatalogueEntity::getCode))
                .toList();
    }
}
