package com.thiru.wealthlens.portfolio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Version;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One user's realised profit, loss and charges for one financial year.
 *
 * <p><b>Read-modify-write, so it carries a version.</b> Every buy and every sell loads this whole
 * document, mutates deep inside {@link RealisedProfits}, and saves it back. Without
 * {@code @Version} two trades for the same user and year processed concurrently would both read,
 * both mutate and both write — and the second would silently erase the first's capital gains and
 * charges. Optimistic locking turns that into a detected failure instead of lost money.
 *
 * <p>The failure surfaces as {@code OptimisticLockingFailureException}. There is deliberately no
 * automatic retry: this service is {@code @Transactional} at class level, so a retry loop inside it
 * would re-run inside a transaction already marked for rollback. Retrying needs the transaction
 * boundary moved, which is its own piece of work — and a loud failure is already strictly better
 * than a silent one.
 *
 * <p>Uniqueness on {@code {email, financial_year}} is enforced by an index rather than by
 * convention, because the only read is {@code findByEmailAndFinancialYear} returning an
 * {@code Optional}: two documents for one user-year would make it throw, or quietly answer with one
 * of them while the other's figures became invisible. Created by
 * {@code PortfolioIndexInitializer} — {@code auto-index-creation} is off application-wide, so this
 * annotation alone would create nothing.
 *
 * <p>Not {@code @Data}: an entity with an identity compares by that identity, not by every mutable
 * field it happens to hold. And deliberately no {@code @AllArgsConstructor} — Lombok stamps
 * {@code @ConstructorProperties} onto it, a mapper that picks that constructor bypasses the field
 * initialisers, and {@code auditMetadata} arrives null with nothing to write into. That is the
 * defect ADR-27 records against the charge seeder, and the two hand-written constructors below are
 * what this class actually needs.
 */
@Document(value = "profit_and_loss")
@CompoundIndex(name = "pnl_user_year_idx", def = "{'email': 1, 'financial_year': 1}", unique = true)
@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class ProfitAndLossEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    @Setter(AccessLevel.NONE)
    private String id;

    @Field("email")
    @ValueConverter(SearchableIdentifier.class)
    private String email;

    @Field("financial_year")
    private String financialYear;

    @Field("realised_profits")
    private RealisedProfits realisedProfits;

    @Field("out_sourced_realised_profits")
    private RealisedProfits outSourcedRealisedProfits;

    /** Incremented by Spring Data on every save; a stale value fails the write. */
    @JsonIgnore
    @Version
    @Setter(AccessLevel.NONE)
    @Field("version")
    private Long version;

    @Field("audit_metadata")
    @Setter(AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    /**
     * The {@code trade_outcomes} rows already in this document's figures, which the one-time V2 backfill
     * ({@code ProfitAndLossV2Backfill}, runbook §2.8) skips. Mapped here so a save keeps it: a save replaces the whole
     * document, and an unmapped field went with it, so the backfill's second run could add a sale again. A rebuild
     * fills it with every row it folds (W-283 C3a, D-575). Removed with the backfill (W-173).
     */
    @JsonIgnore
    @Field("pnl_backfilled_outcome_ids")
    @Setter(AccessLevel.NONE)
    private List<String> backfilledOutcomeIds = new ArrayList<>();

    public ProfitAndLossEntity(String email) {
        this.email = email;
    }

    public ProfitAndLossEntity(String email, String financialYear) {
        this.email = email;
        this.financialYear = financialYear;
    }
}
