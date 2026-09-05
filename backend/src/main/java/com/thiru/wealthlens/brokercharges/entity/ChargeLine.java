package com.thiru.wealthlens.brokercharges.entity;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One computed charge, as it would appear on a contract note.
 *
 * <p>Carries the rate and the base it was applied to, not merely the result, so a stored charge can
 * be re-derived and audited rather than taken on trust.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargeLine {

    @Field("code")
    private String code;

    @Field("display_name")
    private String displayName;

    @Field(name = "category", targetType = FieldType.STRING)
    private ChargeCategory category;

    @Field(name = "basis", targetType = FieldType.STRING)
    private ChargeBasis basis;

    /** Whether this came from the broker's rate card or from the instrument itself. */
    @Field(name = "source", targetType = FieldType.STRING)
    private ChargeRuleSource source;

    /** The percentage applied, where one was. */
    @Field("rate")
    private Double rate;

    /** The amount the rate was applied to. */
    @Field("base_amount")
    private double baseAmount;

    /** The computed charge, after modifiers and rounding. */
    @Field("amount")
    private double amount;

    @Field("taxable")
    private boolean taxable;
}
