package com.thiru.wealthlens.brokercharges.entity;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One computed charge, as it would appear on a contract note.
 */
@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargeLine {

    @Column(name = "code")
    private String code;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private ChargeCategory category;

    @Column(name = "basis")
    @Enumerated(EnumType.STRING)
    private ChargeBasis basis;

    /** Whether this came from the broker's rate card or from the instrument itself. */
    @Column(name = "source")
    @Enumerated(EnumType.STRING)
    private ChargeRuleSource source;

    /** The percentage applied, where one was. */
    @Column(name = "rate")
    private Double rate;

    /** The amount the rate was applied to. */
    @Column(name = "base_amount")
    private double baseAmount;

    /** The computed charge, after modifiers and rounding. */
    @Column(name = "amount")
    private double amount;

    @Column(name = "taxable")
    private boolean taxable;
}
