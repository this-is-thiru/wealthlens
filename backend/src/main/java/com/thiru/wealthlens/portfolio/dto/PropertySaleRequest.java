package com.thiru.wealthlens.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The sale of a land or building a user bought, entered once, at the sale (W-088g9, D-434). Every amount is the user's
 * own: a co-owner enters their share, and a part sold enters that part's price and cost. An expense map's keys are
 * {@link com.thiru.wealthlens.portfolio.dto.enums.PropertyExpense} names.
 */
@Data
@NoArgsConstructor
@ToString
public class PropertySaleRequest {

    private String propertyName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    private LocalDate acquiredOn;

    private double costRupees;

    private Map<String, Double> acquisitionExpenses = new LinkedHashMap<>();

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    private LocalDate transferredOn;

    private double considerationRupees;

    private Map<String, Double> transferExpenses = new LinkedHashMap<>();
}
