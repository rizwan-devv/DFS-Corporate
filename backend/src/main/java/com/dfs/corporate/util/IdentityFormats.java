package com.dfs.corporate.util;

/**
 * Normalize identity fields for DFS Account API (plain values, no dashes/hashes).
 */
public final class IdentityFormats {

    private IdentityFormats() {}

    /** CNIC / NID: digits only (37405-4969028-5 → 3740549690285). */
    public static String cnicDigits(String cnic) {
        if (cnic == null) return null;
        String digits = cnic.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Phone: keep digits and leading + only. */
    public static String phoneDigits(String phone) {
        if (phone == null) return null;
        String n = phone.replaceAll("[^0-9+]", "").trim();
        return n.isEmpty() ? null : n;
    }

    /** Wallet / mPIN: digits only, plain (never hashed). */
    public static String pinPlain(String pin) {
        if (pin == null) return null;
        String digits = pin.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Password for DFS partners[]: trim only — must stay plain text. */
    public static String passwordPlain(String password) {
        if (password == null) return null;
        String t = password.trim();
        return t.isEmpty() ? null : t;
    }
}
