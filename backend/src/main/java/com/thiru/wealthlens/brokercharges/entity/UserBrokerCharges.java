package com.thiru.wealthlens.brokercharges.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;


/**
 * <b>Superseded.</b> One user's charges under the superseded implementation.
 *
 * <p>Replaced by {@code UserChargeEntity}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(value = "user_broker_charges")
public class UserBrokerCharges implements AuditableEntity {
    @JsonIgnore
    @MongoId
    private String id;

    @Field("email")
    private String email;

    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    @Field("stock_code")
    private String stockCode;

    /**
     * The person whose demat account the trade settled in. Part of the depository-charge
     * deduplication key: a DP charge is levied per demat account, so the same scrip sold on the
     * same day under two account holders attracts two charges, not one.
     */
    @Field("account_holder")
    private String accountHolder;

    @Field("transaction_date")
    private LocalDate transactionDate;

    @Field(name = "type", targetType = FieldType.STRING)
    private BrokerChargeTransactionType type;

    @Field("transaction_id")
    private String transactionId;

    @Field("brokerage")
    private double brokerage;

    @Field("account_opening_charges")
    private double accountOpeningCharges;

    @Field("amc_charges")
    private double amcCharges;

    @Field("govt_charges")
    private double govtCharges;

    @Field("taxes")
    private double taxes;

    @Field("dp_charges")
    private double dpCharges;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
