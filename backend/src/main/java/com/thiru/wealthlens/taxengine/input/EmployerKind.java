package com.thiru.wealthlens.taxengine.input;

/**
 * Who pays the salary, as far as a built rule asks ({@code DR 05} M3.2's {@code employerType}; D-105): 1961 s.16(ii)
 * allows its deduction only to an assessee "in receipt of a salary from the Government".
 *
 * <p>Not named {@code EmployerType}: {@code taxplanning.enums.EmployerType} holds the legacy form of the same concept,
 * and this closed module may not depend on it, while one simple name per class is enforced repo-wide.
 */
public enum EmployerKind {

    /** The Central Government or a State Government. */
    GOVERNMENT,

    /** Any other employer. */
    OTHER
}
