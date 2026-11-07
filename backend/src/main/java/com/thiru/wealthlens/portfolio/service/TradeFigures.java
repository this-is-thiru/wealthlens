package com.thiru.wealthlens.portfolio.service;

/**
 * One matched portion's charges and values, as its writer computed them. Each writer keeps its own formula — the
 * migration and V1 unscaled, V2 scaled to the paisa — so this carries figures and computes none.
 */
record TradeFigures(double buyBrokerCharges, double buyMiscCharges, double sellBrokerCharges,
        double sellMiscCharges, double totalBuyValue, double totalSellValue, double netProfit,
        double profitPercentage) {
}
