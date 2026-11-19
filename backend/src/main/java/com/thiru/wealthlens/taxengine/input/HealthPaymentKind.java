package com.thiru.wealthlens.taxengine.input;

/** What a health payment under s.126 is for (S2 s.126(2); D-136). */
public enum HealthPaymentKind {

    /** A premium to effect or keep in force health insurance — s.126(2)(a), (b). */
    INSURANCE_PREMIUM,

    /** A contribution to the Central Government Health Scheme or another notified scheme — s.126(2)(a). */
    GOVERNMENT_HEALTH_SCHEME,

    /** A preventive health check-up — s.126(2)(a), (b), limited together by s.126(3). */
    PREVENTIVE_CHECKUP,

    /** Medical expenditure on someone's health — s.126(2)(c), (d). */
    MEDICAL_EXPENDITURE
}
