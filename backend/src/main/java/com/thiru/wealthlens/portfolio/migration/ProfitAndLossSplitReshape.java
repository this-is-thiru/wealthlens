package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.SplitReshapeReport;
import com.thiru.wealthlens.portfolio.dto.SplitReshapeReport.PlacedSide;
import com.thiru.wealthlens.portfolio.dto.SplitReshapeReport.UnplacedSide;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Moves each P&L side's stored short / long pair into {@code gains_by_asset_and_segment} and unsets the pair and
 * {@code gains_by_classification}, which nothing has read since W-160d2 (D-366 C2a, W-160d3).
 *
 * <p>The figures are the pair's own, so no amount a user saw changes; {@code trade_outcomes} gives only the facts the
 * pair summed away. A side is placed when its year's outcomes for that account side share one asset type and segment
 * (no segment is the entry default, delivery, D-295), and is otherwise left as it is and listed (C1a, C2a). The pair is
 * added to the split, since sales made after the deploy may already be there. Field names are literals because the
 * entity no longer maps the old ones.
 *
 * <p>Idempotent: a placed side keeps no old field, so a second run — including after one that failed part-way —
 * places only what is left. Run when a {@code SUPER_USER} asks ({@code POST /profit-and-loss/split/reshape}), never at
 * startup (D-280); the caller is recorded on each document changed. One-time: removed once its run is logged for every
 * database (W-170).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ProfitAndLossSplitReshape {

    private static final String COLLECTION = "profit_and_loss";
    private static final String SELF_SIDE = "realised_profits";
    private static final String OUTSOURCED_SIDE = "out_sourced_realised_profits";
    private static final String SHORT = "short_term_capital_gains";
    private static final String LONG = "long_term_capital_gains";
    private static final String BY_CLASSIFICATION = "gains_by_classification";
    private static final String SPLIT = "gains_by_asset_and_segment";
    private static final Pattern FINANCIAL_YEAR = Pattern.compile("(\\d{4})-(\\d{4})");

    private final MongoTemplate mongoTemplate;

    /** Place every side still carrying the pair or the map, unless {@code dryRun}; {@code auditor} is who asked. */
    public SplitReshapeReport reshape(boolean dryRun, String auditor) {
        List<PlacedSide> placed = new ArrayList<>();
        List<UnplacedSide> unplaced = new ArrayList<>();
        for (Document pnl : mongoTemplate.find(Query.query(carryingOldFields()), Document.class, COLLECTION)) {
            Update update = new Update();
            for (String side : List.of(SELF_SIDE, OUTSOURCED_SIDE)) {
                reshapeSide(pnl, side, update, placed, unplaced);
            }
            if (!dryRun && !update.getUpdateObject().isEmpty()) {
                update.set("audit_metadata.updatedBy", auditor).set("audit_metadata.updatedAt", TLocalDateTime.now());
                mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(pnl.get("_id"))), update, COLLECTION);
            }
        }
        log.info("P&L split reshape by {} (dry run {}): {} sides placed, {} left", auditor, dryRun, placed.size(),
                unplaced.size());
        return new SplitReshapeReport(dryRun, auditor, placed, unplaced);
    }

    private static Criteria carryingOldFields() {
        List<Criteria> any = new ArrayList<>();
        for (String side : List.of(SELF_SIDE, OUTSOURCED_SIDE)) {
            for (String field : List.of(SHORT, LONG, BY_CLASSIFICATION)) {
                any.add(Criteria.where(side + "." + field).exists(true));
            }
        }
        return new Criteria().orOperator(any);
    }

    private void reshapeSide(Document pnl, String side, Update update, List<PlacedSide> placed,
            List<UnplacedSide> unplaced) {
        Document realised = pnl.get(side, Document.class);
        if (realised == null || !(realised.containsKey(SHORT) || realised.containsKey(LONG)
                || realised.containsKey(BY_CLASSIFICATION))) {
            return;
        }
        String email = pnl.getString("email");
        String year = pnl.getString("financial_year");
        Document pair = StoredReport.sum(realised.get(SHORT, Document.class), realised.get(LONG, Document.class));
        if (pair == null) {
            unsetOldFields(update, side);
            return;
        }
        Placement placement = placementOf(email, year, side);
        if (placement.reason().isPresent()) {
            unplaced.add(new UnplacedSide(email, year, side, placement.reason().get()));
            return;
        }
        String path = SPLIT + "." + placement.assetType() + "." + placement.segment();
        Document existing = Optional.ofNullable(realised.get(SPLIT, Document.class))
                .map(split -> split.get(placement.assetType().name(), Document.class))
                .map(bySegment -> bySegment.get(placement.segment().name(), Document.class))
                .orElse(null);
        update.set(side + "." + path, StoredReport.sum(existing, pair));
        unsetOldFields(update, side);
        placed.add(new PlacedSide(email, year, side, placement.assetType(), placement.segment()));
    }

    private static void unsetOldFields(Update update, String side) {
        update.unset(side + "." + SHORT).unset(side + "." + LONG).unset(side + "." + BY_CLASSIFICATION);
    }

    /** The one asset type and segment the side's outcomes in the year share, or why there is none. */
    private Placement placementOf(String email, String year, String side) {
        Matcher matcher = FINANCIAL_YEAR.matcher(year == null ? "" : year);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) != Integer.parseInt(matcher.group(1)) + 1) {
            return Placement.refused("the financial year " + year + " cannot be read");
        }
        int startYear = Integer.parseInt(matcher.group(1));
        Criteria account = SELF_SIDE.equals(side) ? Criteria.where("accountType").is(AccountType.SELF)
                : Criteria.where("accountType").ne(AccountType.SELF);
        List<TradeOutcomeEntity> rows = mongoTemplate.find(Query.query(Criteria.where("email").is(email)
                .and("sellDate").gte(LocalDate.of(startYear, 4, 1)).lte(LocalDate.of(startYear + 1, 3, 31))
                .andOperator(account)), TradeOutcomeEntity.class);
        if (rows.isEmpty()) {
            return Placement.refused("no trade outcomes in the year");
        }
        if (rows.stream().anyMatch(row -> row.getAssetType() == null)) {
            return Placement.refused("a trade outcome records no asset type");
        }
        Set<String> facts = new TreeSet<>();
        rows.forEach(row -> facts.add(row.getAssetType() + "/" + segmentOf(row)));
        if (facts.size() > 1) {
            return Placement.refused("trade outcomes of more than one asset type and segment: " + facts);
        }
        return Placement.of(rows.getFirst().getAssetType(), segmentOf(rows.getFirst()));
    }

    /** The sale's segment, else the row's, else the entry default (D-295). */
    private static TradeSegment segmentOf(TradeOutcomeEntity row) {
        return Optional.ofNullable(row.getSellSegment()).or(() -> Optional.ofNullable(row.getSegment()))
                .orElse(TradeSegment.DELIVERY);
    }

    private record Placement(AssetType assetType, TradeSegment segment, Optional<String> reason) {

        static Placement of(AssetType assetType, TradeSegment segment) {
            return new Placement(assetType, segment, Optional.empty());
        }

        static Placement refused(String reason) {
            return new Placement(null, null, Optional.of(reason));
        }
    }
}
