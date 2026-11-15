package com.thiru.wealthlens.taxengine.api;

/**
 * How the three instruments governing a tax year are cited (W-123a C3a): the report names them rather than "the Act",
 * because the rates, the machinery and the salaried values each come from a different one.
 *
 * @param act the Income-tax Act
 * @param financeAct the Finance Act that sets the year's rates
 * @param rules the Income-tax Rules
 */
public record InstrumentCitations(String act, String financeAct, String rules) {
}
