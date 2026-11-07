package com.thiru.wealthlens.taxengine.input;

/** What the vehicle is used for, as Table II distinguishes it (S4 r.15(3); Rules 1962 r.3(2); D-113). */
public enum VehicleUse {

    /** "Wholly and exclusively in the performance of his official duties". */
    WHOLLY_OFFICIAL,

    /** "Exclusively for the private or personal purposes of the employee or any member of his household". */
    WHOLLY_PRIVATE,

    /** "Partly in the performance of duties and partly for private or personal purposes". */
    PARTLY_OFFICIAL
}
