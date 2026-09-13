package com.osrsfliphub;

import java.util.Locale;

/**
 * The five ways a Grand Exchange trader turns items into other items.
 */
enum ConversionKind {
    ASSEMBLE,
    DISASSEMBLE,
    REPAIR,
    SET_COMBINE,
    SET_BREAK;

    static ConversionKind parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.US);
        for (ConversionKind kind : values()) {
            if (kind.name().equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
