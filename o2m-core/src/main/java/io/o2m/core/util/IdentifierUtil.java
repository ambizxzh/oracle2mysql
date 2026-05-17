package io.o2m.core.util;

import io.o2m.core.config.RulesConfig;

public final class IdentifierUtil {
    private IdentifierUtil() {}

    public static String toMysql(String name, RulesConfig rules) {
        if (name == null) return "";
        String n = "upper".equalsIgnoreCase(rules.getIdentifierCase()) ? name.toUpperCase() : name.toLowerCase();
        return rules.isQuoteIdentifiers() ? "`" + n + "`" : n;
    }

    public static String toOracle(String name) {
        return name == null ? "" : name.toUpperCase();
    }

    public static String safeConstraintName(String name, int maxLen) {
        if (name == null || name.length() <= maxLen) return name;
        return name.substring(0, Math.max(1, maxLen - 8)) + "_" + Integer.toHexString(name.hashCode() & 0xFFFF);
    }

    public static String escapeComment(String comment) {
        if (comment == null) return "";
        return comment.replace("\\", "\\\\").replace("'", "''");
    }

    public static String escapeSqlString(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }
}
