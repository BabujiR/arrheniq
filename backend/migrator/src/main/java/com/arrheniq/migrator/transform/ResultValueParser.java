package com.arrheniq.migrator.transform;

import com.arrheniq.migrator.transform.ParsedValue.Qualifier;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses legacy VARCHAR result replicates into structured values (legacy-mapping.md §7; URS-RES-002, URS-MIG-001).
 * <p>
 * Rules (in order):
 * <ol>
 *   <li>blank → empty (no replicate row is created)</li>
 *   <li>text tests (results format 'T') → text value as-is (QuickList code resolved by the caller)</li>
 *   <li>plain number incl. sign and exponent → numeric</li>
 *   <li>{@code <x} / {@code >x} → numeric with LT/GT qualifier</li>
 *   <li>known abbreviations (built-in + tenant ABBREVIATION dictionary) → qualifier only</li>
 *   <li>anything else → text value with {@code parsed=false} (reported as MIG-E003)</li>
 * </ol>
 * The parser never guesses: unknown input is preserved and reported, not coerced.
 */
public final class ResultValueParser {

    private static final Pattern NUMBER = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$");
    private static final Pattern COMPARATOR = Pattern.compile("^([<>])\\s*([+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?)$");

    private static final Map<String, Qualifier> BUILT_IN = Map.ofEntries(
            Map.entry("ND", Qualifier.ND),
            Map.entry("N.D.", Qualifier.ND),
            Map.entry("NOT DETECTED", Qualifier.ND),
            Map.entry("BQL", Qualifier.LOQ),
            Map.entry("BLQ", Qualifier.LOQ),
            Map.entry("<LOQ", Qualifier.LOQ),
            Map.entry("< LOQ", Qualifier.LOQ),
            Map.entry("<LOD", Qualifier.LOD),
            Map.entry("< LOD", Qualifier.LOD),
            Map.entry("NT", Qualifier.NT),
            Map.entry("NOT TESTED", Qualifier.NT),
            Map.entry("NA", Qualifier.NA),
            Map.entry("N/A", Qualifier.NA));

    private final Map<String, Qualifier> tenantAbbreviations;

    /**
     * @param tenantAbbreviations upper-cased abbreviation code → qualifier, from the migrated ABBREVIATION dictionary
     */
    public ResultValueParser(Map<String, Qualifier> tenantAbbreviations) {
        this.tenantAbbreviations = Map.copyOf(tenantAbbreviations);
    }

    public Optional<ParsedValue> parse(String raw, boolean textTest) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.strip();
        if (textTest) {
            return Optional.of(ParsedValue.text(value, raw, true));
        }
        if (NUMBER.matcher(value).matches()) {
            return Optional.of(ParsedValue.numeric(new BigDecimal(value), raw));
        }
        String upper = value.toUpperCase(Locale.ROOT);
        Qualifier abbreviation = lookup(upper);
        if (abbreviation != null) {
            return Optional.of(ParsedValue.qualified(null, abbreviation, raw));
        }
        Matcher m = COMPARATOR.matcher(value);
        if (m.matches()) {
            Qualifier q = "<".equals(m.group(1)) ? Qualifier.LT : Qualifier.GT;
            return Optional.of(ParsedValue.qualified(new BigDecimal(m.group(2)), q, raw));
        }
        return Optional.of(ParsedValue.text(value, raw, false));
    }

    private Qualifier lookup(String upper) {
        Qualifier q = tenantAbbreviations.get(upper);
        return q != null ? q : BUILT_IN.get(upper);
    }
}
