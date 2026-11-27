package com.thiru.wealthlens.corporate.entity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class DemergerDetail {

    @Column(name = "demerger_ratio")
    private String demergerRatio;

    @Column(name = "demerger_price_ratio")
    private String demergerPriceRatio;

    @Column(name = "main_stock_code")
    private String mainStockCode;

    @Column(name = "main_stock_name")
    private String mainStockName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "corporate_action_demerger_stocks", joinColumns = @JoinColumn(name = "corporate_action_id"))
    private List<DemergerStock> demergerStocks = new ArrayList<>();

    /** For {@code CorporateActionEntity}'s own check only: the detail answers as itself, and m1.6.1 answered no {@code empty}. */
    @JsonIgnore
    public boolean isEmpty() {
        return demergerRatio == null && demergerPriceRatio == null && mainStockCode == null && mainStockName == null
                && (demergerStocks == null || demergerStocks.isEmpty());
    }

    @Embeddable
    @Getter
    @Setter
    public static class DemergerStock {

        @Column(name = "stock_code")
        private String stockCode;

        @Column(name = "stock_name")
        private String stockName;
    }
}
