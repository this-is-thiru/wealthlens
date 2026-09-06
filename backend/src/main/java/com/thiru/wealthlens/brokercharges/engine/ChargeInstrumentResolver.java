package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Finds the instrument's own charges and attributes as they stood on the trade date.
 *
 * <p>Simpler than the schedule resolver because there is nothing to rank — a profile is keyed on the
 * stock code and the only question is which version was in force. Overlapping versions are a data
 * error; the later start date is the one published second and wins, and two sharing a start date are
 * indistinguishable and refused.
 *
 * <p>An absent profile is not an error here. Blocking a quarterly upload because reference data has
 * not been loaded is the wrong trade; the engine records the gap instead.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ChargeInstrumentResolver {

    private final ChargeInstrumentRepository chargeInstrumentRepository;

    private final Map<InstrumentKey, Optional<ChargeInstrumentEntity>> cache = new ConcurrentHashMap<>();

    public Optional<ChargeInstrumentEntity> resolve(ChargeContext context) {
        if (context.stockCode() == null || context.stockCode().isBlank()) {
            // An account-level event such as an annual maintenance charge names no scrip.
            return Optional.empty();
        }
        return cache.computeIfAbsent(
                new InstrumentKey(context.stockCode(), context.transactionDate()), this::select);
    }

    /** Called when an instrument profile is written, so the next trade sees the new version. */
    public void evictAll() {
        cache.clear();
    }

    private Optional<ChargeInstrumentEntity> select(InstrumentKey key) {
        List<ChargeInstrumentEntity> candidates =
                chargeInstrumentRepository.findCandidates(key.stockCode(), key.transactionDate());

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }

        LocalDate latest = candidates.stream()
                .map(ChargeInstrumentEntity::getStartDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<ChargeInstrumentEntity> newest = candidates.stream()
                .filter(candidate -> Objects.equals(candidate.getStartDate(), latest))
                .toList();

        if (newest.size() > 1) {
            String ids = newest.stream().map(ChargeInstrumentEntity::getId).collect(Collectors.joining(", "));
            throw new BadRequestException("More than one instrument profile is in force for "
                    + key.stockCode() + " on " + key.transactionDate() + ": " + ids
                    + ". Close one of their validity windows.");
        }
        return Optional.of(newest.getFirst());
    }

    private record InstrumentKey(String stockCode, LocalDate transactionDate) {
    }
}
