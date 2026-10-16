package com.ngockhanh.clinic.catalog.domain.valueobject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalised catalog code entered by the clinic manager.
 *
 * <p>A code is trimmed and uppercased (ROOT locale), must match {@code [A-Z0-9][A-Z0-9_.-]{0,49}}
 * (1–50 characters after normalisation), and is immutable after creation. Two codes are equal when
 * their canonical forms are equal (case-insensitive, no leading/trailing whitespace).
 */
public final class CatalogCode {
    private static final Pattern PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_.\\-]{0,49}");
    private static final int MAX_LENGTH = 50;

    private final String value;
    private final String canonical;

    public CatalogCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Catalog code must not be blank");
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT);
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Catalog code must not exceed " + MAX_LENGTH + " characters");
        }
        if (!PATTERN.matcher(normalised).matches()) {
            throw new IllegalArgumentException(
                    "Catalog code must start with a letter or digit and contain only letters, digits, underscores, dots and hyphens");
        }
        this.value = normalised;
        this.canonical = normalised;
    }

    private CatalogCode(String stored, String canonical) {
        this.value = stored;
        this.canonical = canonical;
    }

    /**
     * Validates an existing code while preserving its stored spelling for immutable legacy rows.
     *
     * @param stored code read from persistence
     * @return code whose equality uses its canonical form and whose value retains stored spelling
     * @throws IllegalArgumentException if the stored code violates the catalog code contract
     */
    public static CatalogCode restore(String stored) {
        CatalogCode validated = new CatalogCode(stored);
        return new CatalogCode(stored, validated.canonical);
    }

    /**
     * Creates a {@link CatalogCode} from a raw user-supplied string.
     *
     * @param raw the raw code, trimmed and uppercased before validation
     * @throws IllegalArgumentException if the code is blank or does not match the allowed pattern
     */
    public static CatalogCode of(String raw) {
        return new CatalogCode(raw);
    }

    /**
     * Returns canonical spelling for new codes, or the unchanged stored spelling for legacy codes.
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof CatalogCode cc && canonical.equals(cc.canonical));
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
