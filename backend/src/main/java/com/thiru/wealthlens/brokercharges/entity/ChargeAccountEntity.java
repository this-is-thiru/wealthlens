package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A demat account, for charges that attach to the account rather than to a trade
 * (ported to PostgreSQL in W-337d2, D-947; Flyway {@code V8__brokercharges.sql}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "charge_accounts")
@EntityListeners(AuditingEntityListener.class)
public class ChargeAccountEntity {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "email", nullable = false)
    private String email;

    /** Whose account this is. Matches the value carried on transactions. */
    @Column(name = "account_holder")
    private String accountHolder;

    @Column(name = "broker_name")
    @Enumerated(EnumType.STRING)
    private BrokerName brokerName;

    @Column(name = "demat_account_id")
    private String dematAccountId;

    /** Rate variant this account is on, where the broker offers more than one. */
    @Column(name = "plan_code")
    private String planCode;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "amc_frequency")
    @Enumerated(EnumType.STRING)
    private AmcChargeFrequency amcFrequency;

    /** The last date covered by an annual-maintenance charge. Cycles up to it are not billed again. */
    @Column(name = "last_billed_through")
    private LocalDate lastBilledThrough;

    /** One entry per billing cycle, each pointing at the {@code user_charges} row it produced. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "charge_account_billing_events", joinColumns = @JoinColumn(name = "account_id"))
    private List<BillingEvent> billingEvents = new ArrayList<>();

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    @Embedded
    @Setter(value = AccessLevel.NONE)
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    /** One completed billing cycle. */
    @Embeddable
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BillingEvent {

        @Column(name = "user_charge_id")
        private String userChargeId;

        @Column(name = "period_from")
        private LocalDate periodFrom;

        @Column(name = "period_to")
        private LocalDate periodTo;

        @Column(name = "charged_on")
        private LocalDate chargedOn;

        @Column(name = "amount")
        private double amount;
    }
}
