package com.thiru.wealthlens.taxengine.input;

/** The class of an asset the employer transfers, which sets its wear and tear (Table IV Sl. 8; r.3(7)(viii); D-119). */
public enum TransferredAssetKind {

    /** Computers and electronic items — 50% a year, reducing balance. */
    COMPUTER_OR_ELECTRONIC,

    /** Motor cars — 20% a year, reducing balance. */
    MOTOR_CAR,

    /** Any other asset — 10% of cost for each completed year. */
    OTHER
}
