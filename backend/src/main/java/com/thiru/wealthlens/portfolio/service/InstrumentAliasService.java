package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.InstrumentAliasRegistration;
import com.thiru.wealthlens.portfolio.dto.InstrumentAliasRequest;
import com.thiru.wealthlens.portfolio.dto.InstrumentAliasResponse;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.InstrumentAliasKind;
import com.thiru.wealthlens.portfolio.entity.InstrumentAliasEntity;
import com.thiru.wealthlens.portfolio.repository.InstrumentAliasRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The instrument registry's aliases (W-440b, D-1055, C14a C15a): which stock code each value a broker's file writes names, so an import
 * finds a trade's instrument by what the file says rather than by how it looks. Strict until the owner eases it: a value is kept
 * exactly as written, an ISIN has the shape every ISIN in the owner's Zerodha files has, and a value never names two codes.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class InstrumentAliasService {

    /** Two letters, nine letters or digits, a digit: all twelve ISINs of the owner's Zerodha equity and fund tradebooks. */
    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final InstrumentAliasRepository instrumentAliasRepository;

    /**
     * Writes each alias not yet on file and skips each already naming the same code. Any refusal refuses the whole list, so a
     * correction is never half applied.
     */
    public InstrumentAliasRegistration register(List<InstrumentAliasRequest> aliases) {
        Map<AliasKey, String> inThisList = new HashMap<>();
        aliases.forEach(alias -> requireShape(alias, inThisList));

        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<InstrumentAliasEntity> toWrite = new ArrayList<>();
        for (InstrumentAliasRequest alias : aliases) {
            InstrumentAliasEntity onFile =
                    instrumentAliasRepository.findAlias(alias.kind(), alias.brokerName(), alias.value()).orElse(null);
            if (onFile == null) {
                toWrite.add(new InstrumentAliasEntity(null, alias.kind(), alias.brokerName(), alias.value(), alias.stockCode(), null));
                created.add(alias.describe());
            } else if (onFile.getStockCode().equals(alias.stockCode())) {
                skipped.add(alias.describe());
            } else {
                throw new BadRequestException(alias.kind() + " " + alias.value() + " already names " + onFile.getStockCode()
                        + ", so it cannot name " + alias.stockCode() + "; nothing was registered.");
            }
        }
        instrumentAliasRepository.saveAll(toWrite);
        log.info("Instrument aliases: {} registered, {} already on file", created.size(), skipped.size());
        return new InstrumentAliasRegistration(created, skipped);
    }

    /** Every alias, by stock code then value, so the owner reads one instrument's together. */
    @Transactional(readOnly = true)
    public Page<InstrumentAliasResponse> aliases(Pageable pageable) {
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("stockCode", "value", "kind", "brokerName"));
        return instrumentAliasRepository.findAll(ordered).map(InstrumentAliasResponse::from);
    }

    private static void requireShape(InstrumentAliasRequest alias, Map<AliasKey, String> inThisList) {
        requireAsWritten("value", alias.value());
        requireAsWritten("stockCode", alias.stockCode());
        if (alias.kind().isBrokersOwn() && alias.brokerName() == null) {
            throw new BadRequestException("A " + alias.kind() + " is the broker's own, so it needs a broker: " + alias.value());
        }
        if (alias.kind() == InstrumentAliasKind.ISIN && alias.brokerName() != null) {
            throw new BadRequestException("An ISIN is the instrument's at every broker, so it names no broker: " + alias.value());
        }
        if (alias.kind() == InstrumentAliasKind.ISIN && !ISIN.matcher(alias.value()).matches()) {
            throw new BadRequestException("Not an ISIN (two capital letters, nine capital letters or digits, a digit): " + alias.value());
        }
        String earlier = inThisList.putIfAbsent(new AliasKey(alias), alias.stockCode());
        if (earlier != null && !earlier.equals(alias.stockCode())) {
            throw new BadRequestException(alias.kind() + " " + alias.value() + " is listed for both " + earlier + " and "
                    + alias.stockCode() + "; nothing was registered.");
        }
    }

    private static void requireAsWritten(String field, String text) {
        if (!text.equals(text.strip())) {
            throw new BadRequestException(field + " has a space before or after it, which no broker's file writes: '" + text + "'");
        }
    }

    private record AliasKey(InstrumentAliasKind kind, BrokerName brokerName, String value) {

        AliasKey(InstrumentAliasRequest alias) {
            this(alias.kind(), alias.brokerName(), alias.value());
        }
    }
}
