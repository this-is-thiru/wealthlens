package com.thiru.wealthlens.taxengine.api;

/**
 * One question a year's return asks the person and the engine cannot compute, as a reader outside the engine needs it
 * to ask (W-090a2, D-520 C2a). A fact read from the year's pack; which form it bars is not said here.
 *
 * @param code the question, as an answer names it
 * @param question what the person is asked
 * @param answerKind {@code YES_NO} or {@code AMOUNT}
 * @param source the clause of the law that asks it
 */
public record DeclarationQuestion(String code, String question, String answerKind, String source) {
}
