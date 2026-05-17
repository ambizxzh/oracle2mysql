package io.o2m.core.util;

import io.o2m.model.CheckConstraintMetadata;
import io.o2m.model.TableMetadata;

public final class ConstraintUtil {
    private ConstraintUtil() {}

    /** Oracle NOT NULL enforcement often appears as CHECK; column NOT NULL makes these redundant on MySQL. */
    public static boolean isRedundantNotNullCheck(CheckConstraintMetadata chk, TableMetadata table) {
        if (chk == null || chk.expression() == null) return false;
        String upper = chk.expression().toUpperCase();
        if (!upper.contains("IS NOT NULL")) return false;
        for (var col : table.columns()) {
            if (!col.nullable() && upper.contains(col.name().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /** InnoDB exposes PRIMARY KEY constraint name as PRIMARY regardless of DDL label. */
    public static boolean isMysqlPrimaryKeyAlias(String constraintName) {
        return constraintName != null && "PRIMARY".equalsIgnoreCase(constraintName.trim());
    }
}
