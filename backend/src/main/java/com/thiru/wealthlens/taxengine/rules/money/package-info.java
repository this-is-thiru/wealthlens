/**
 * {@code Money} and {@code Rate} — exact rupee amounts and statutory rates. A named interface
 * because every consumer of a result handles money; {@code propagate} does not carry these through
 * {@code input}/{@code result}, as the W-045b2 probe showed (decision D-048).
 */
@org.springframework.modulith.NamedInterface("money")
package com.thiru.wealthlens.taxengine.rules.money;
