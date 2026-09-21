package com.dfs.corporate.domain;

/** Where a watchlist row came from. Official files are imported; not scraped. */
public enum AmlListSource {
    UN,
    OFAC,
    NACTA,
    INTERNAL
}
