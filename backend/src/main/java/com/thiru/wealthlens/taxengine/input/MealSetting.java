package com.thiru.wealthlens.taxengine.input;

/**
 * Where and how the employer provides food and non-alcoholic beverages — which decides how much of it is salary (S4
 * r.15(5) Table IV Sl. 3; Rules 1962 r.3(7)(iii); D-117).
 */
public enum MealSetting {

    /** During working hours at office or business premises: salary only beyond the limit per meal. */
    AT_WORK,

    /** Through paid vouchers usable only at eating joints: as at work, save where the year's Rule bars the regime. */
    PAID_VOUCHER,

    /** Tea or snacks during working hours: never salary. */
    TEA_OR_SNACKS_AT_WORK,

    /** During working hours in a remote area or an off-shore installation: never salary. */
    AT_WORK_REMOTE_OR_OFFSHORE,

    /** Any other food the employer provides: salary in full. */
    OTHERWISE
}
