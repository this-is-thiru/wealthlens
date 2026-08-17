package com.thiru.wealthlens.corporate.entity.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
public class DemergerDetail {
    @Field("demerger_ratio")
    private String demergerRatio;

    @Field("demerger_price_ratio")
    private String demergerPriceRatio;

    @Field("main_stock_code")
    private String mainStockCode;

    @Field("main_stock_name")
    private String mainStockName;

    @Field("demerger_stocks")
    private List<DemergerStock> demergerStocks;

    @Getter
    @Setter
    public static class DemergerStock {
        @Field("stock_code")
        private String stockCode;

        @Field("stock_name")
        private String stockName;
    }
}
