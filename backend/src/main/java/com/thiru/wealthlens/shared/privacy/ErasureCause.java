package com.thiru.wealthlens.shared.privacy;

/** What started an account's erasure (Digital Personal Data Protection Act, 2023 s.8(7)(a), s.12(3); W-249). */
public enum ErasureCause {

    /** The person asked (s.12(3); W-237d). */
    REQUESTED,

    /** No sign-in for {@code app.privacy.purpose-lapse}: the purpose is no longer served (s.8(7)(a)). */
    PURPOSE_LAPSED
}
