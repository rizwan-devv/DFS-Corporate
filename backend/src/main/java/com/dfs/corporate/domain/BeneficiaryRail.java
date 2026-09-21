package com.dfs.corporate.domain;

public enum BeneficiaryRail {
    /** Intra / fund transfer only */
    FT,
    /** Interbank only */
    IBFT,
    /** Both FT and IBFT */
    FT_IBFT,
    /** Raast ID / IBAN style */
    RAAST
}
