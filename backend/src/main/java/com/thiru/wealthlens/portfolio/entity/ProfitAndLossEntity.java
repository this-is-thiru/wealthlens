package com.thiru.wealthlens.portfolio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One user's realised profit, loss and charges for one financial year.
 *
 * <p><b>Read-modify-write, so it carries a version.</b> Every buy and every sell loads this whole
 * row, mutates deep inside {@link RealisedProfits}, and saves it back. Without
 * {@code @Version} two trades for the same user and year processed concurrently would both read,
 * both mutate and both write — and the second would silently erase the first's capital gains and
 * charges. Optimistic locking turns that into a detected failure instead of lost money.
 *
 * <p>The failure surfaces as {@code ObjectOptimisticLockingFailureException}. PostgreSQL detects no conflict at the
 * level of the row's contents, so
 * this {@code @Version} check is the only place two concurrent trades for the same user and year are caught —
 * {@code TradeCollisionRetry} retries on it explicitly (W-337e, D-949 C5a).
 *
 * <p>Uniqueness on {@code {email, financial_year}} is enforced by {@code pnl_user_year_idx}
 * (W-337e, D-949; {@code db/migration/portfolio/V9__portfolio.sql}) rather than by convention, because the only
 * read is {@code findByEmailAndFinancialYear} returning an {@code Optional}: two rows for one user-year would make
 * it throw, or quietly answer with one of them while the other's figures became invisible.
 *
 * <p>Not {@code @Data}: an entity with an identity compares by that identity, not by every mutable
 * field it happens to hold. And deliberately no {@code @AllArgsConstructor} — Lombok stamps
 * {@code @ConstructorProperties} onto it, a mapper that picks that constructor bypasses the field
 * initialisers, and {@code auditMetadata} arrives null with nothing to write into. That is the
 * defect ADR-27 records against the charge seeder, and the two hand-written constructors below are
 * what this class actually needs.
 */
@Entity
@Table(name = "profit_and_loss")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class ProfitAndLossEntity {

    @JsonIgnore
    @Id
    @Setter(AccessLevel.NONE)
    private String id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "financial_year")
    private String financialYear;

    @Column(name = "realised_profits_json")
    @Convert(converter = RealisedProfitsConverter.class)
    private RealisedProfits realisedProfits;

    @Column(name = "out_sourced_realised_profits_json")
    @Convert(converter = RealisedProfitsConverter.class)
    private RealisedProfits outSourcedRealisedProfits;

    /** Incremented by Hibernate on every save; a stale value fails the write. */
    @JsonIgnore
    @Version
    @Setter(AccessLevel.NONE)
    @Column(name = "version")
    private Long version;

    @Embedded
    @Setter(AccessLevel.NONE)
    private AuditColumns auditMetadata = new AuditColumns();

    public ProfitAndLossEntity(String email) {
        this.email = email;
    }

    public ProfitAndLossEntity(String email, String financialYear) {
        this.email = email;
        this.financialYear = financialYear;
    }

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
