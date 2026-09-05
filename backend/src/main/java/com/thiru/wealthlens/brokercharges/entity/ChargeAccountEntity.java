package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A demat account, for charges that attach to the account rather than to a trade.
 *
 * <p>Annual maintenance and account opening are billed per demat account, so a user holding
 * accounts for more than one person is billed once for each. The same reasoning puts
 * {@code accountHolder} in the depository-charge deduplication key.
 *
 * <p>{@code lastBilledThrough} is what makes re-running a quarter safe: a cycle already covered by
 * it is skipped, so an accidental second upload is a no-op rather than a second charge.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "charge_accounts")
@CompoundIndex(name = "charge_account_idx",
        def = "{'email': 1, 'broker_name': 1, 'demat_account_id': 1}", unique = true)
public class ChargeAccountEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

    @Field("email")
    private String email;

    /** Whose account this is. Matches the value carried on transactions. */
    @Field("account_holder")
    private String accountHolder;

    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    @Field("demat_account_id")
    private String dematAccountId;

    /** Rate variant this account is on, where the broker offers more than one. */
    @Field("plan_code")
    private String planCode;

    @Field("opened_on")
    private LocalDate openedOn;

    @Field(name = "amc_frequency", targetType = FieldType.STRING)
    private AmcChargeFrequency amcFrequency;

    /** The last date covered by an annual-maintenance charge. Cycles up to it are not billed again. */
    @Field("last_billed_through")
    private LocalDate lastBilledThrough;

    /** One entry per billing cycle, each pointing at the {@code user_charges} row it produced. */
    @Field("billing_events")
    private List<BillingEvent> billingEvents = new ArrayList<>();

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    /** One completed billing cycle. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BillingEvent {

        @Field("user_charge_id")
        private String userChargeId;

        @Field("period_from")
        private LocalDate periodFrom;

        @Field("period_to")
        private LocalDate periodTo;

        @Field("charged_on")
        private LocalDate chargedOn;

        @Field("amount")
        private double amount;
    }
}
