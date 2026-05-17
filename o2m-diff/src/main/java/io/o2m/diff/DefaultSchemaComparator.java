package io.o2m.diff;

import io.o2m.model.*;
import io.o2m.spi.SchemaComparator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultSchemaComparator implements SchemaComparator {
    @Override
    public DiffResult compare(SchemaSnapshot expected, SchemaSnapshot actual) {
        List<DiffChange> changes = new ArrayList<>();
        Set<String> allTables = new HashSet<>();
        expected.tables().forEach(t -> allTables.add(t.name().toUpperCase()));
        actual.tables().forEach(t -> allTables.add(t.name().toUpperCase()));

        for (String tableName : allTables.stream().sorted().toList()) {
            var exp = expected.findTable(tableName);
            var act = actual.findTable(tableName);
            if (exp.isEmpty()) {
                changes.add(change(tableName, "TABLE", tableName, "exists", "false", "true",
                        DiffSeverity.ERROR, "TABLE_MISSING_EXPECTED"));
                continue;
            }
            if (act.isEmpty()) {
                changes.add(change(tableName, "TABLE", tableName, "exists", "true", "false",
                        DiffSeverity.ERROR, "TABLE_MISSING_ACTUAL"));
                continue;
            }
            compareTable(exp.get(), act.get(), changes);
        }
        return new DiffResult(changes);
    }

    private void compareTable(TableMetadata exp, TableMetadata act, List<DiffChange> changes) {
        String table = exp.name();
        if (!nullToEmpty(exp.comment()).equals(nullToEmpty(act.comment()))) {
            changes.add(change(table, "TABLE", table, "comment", exp.comment(), act.comment(),
                    DiffSeverity.WARNING, "TABLE_COMMENT_DIFF"));
        }
        for (ColumnMetadata ec : exp.columns()) {
            var acOpt = act.findColumn(ec.name());
            if (acOpt.isEmpty()) {
                changes.add(change(table, "COLUMN", ec.name(), "exists", "true", "false",
                        DiffSeverity.ERROR, "COLUMN_MISSING"));
                continue;
            }
            ColumnMetadata ac = acOpt.get();
            String expType = normalizeType(ec.mysqlType());
            String actType = normalizeType(ac.mysqlType());
            if (!expType.equals(actType)) {
                DiffSeverity sev = isAcceptableTypeDiff(expType, actType) ? DiffSeverity.ACCEPTABLE : DiffSeverity.ERROR;
                changes.add(change(table, "COLUMN", ec.name(), "mysqlType", expType, actType, sev, "TYPE_DIFF"));
            }
            if (ec.nullable() != ac.nullable()) {
                changes.add(change(table, "COLUMN", ec.name(), "nullable",
                        String.valueOf(ec.nullable()), String.valueOf(ac.nullable()),
                        DiffSeverity.ERROR, "NULLABLE_DIFF"));
            }
            if (!nullToEmpty(ec.comment()).equals(nullToEmpty(ac.comment()))) {
                changes.add(change(table, "COLUMN", ec.name(), "comment", ec.comment(), ac.comment(),
                        DiffSeverity.WARNING, "COLUMN_COMMENT_DIFF"));
            }
        }
        if (exp.primaryKey() != null) {
            if (act.primaryKey() == null) {
                changes.add(change(table, "CONSTRAINT", "PK", "primaryKey", "present", "missing",
                        DiffSeverity.ERROR, "PK_MISSING"));
            } else if (!columnsEqual(exp.primaryKey().columns(), act.primaryKey().columns())) {
                changes.add(change(table, "CONSTRAINT", "PK", "columns",
                        String.join(",", exp.primaryKey().columns()),
                        String.join(",", act.primaryKey().columns()),
                        DiffSeverity.ERROR, "PK_COLUMNS_DIFF"));
            }
        }
        compareIndexes(table, exp, act, changes);
    }

    private void compareIndexes(String table, TableMetadata exp, TableMetadata act, List<DiffChange> changes) {
        for (IndexMetadata ei : exp.indexes()) {
            if (ei.gap()) continue;
            var match = act.indexes().stream()
                    .filter(ai -> ai.name().equalsIgnoreCase(ei.name()))
                    .findFirst();
            if (match.isEmpty()) {
                changes.add(change(table, "INDEX", ei.name(), "exists", "true", "false",
                        DiffSeverity.ERROR, "INDEX_MISSING"));
            } else if (!columnsEqual(ei.columns(), match.get().columns())) {
                changes.add(change(table, "INDEX", ei.name(), "columns",
                        String.join(",", ei.columns()), String.join(",", match.get().columns()),
                        DiffSeverity.ERROR, "INDEX_COLUMNS_DIFF"));
            }
        }
    }

    private boolean columnsEqual(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equalsIgnoreCase(b.get(i))) return false;
        }
        return true;
    }

    private String normalizeType(String type) {
        if (type == null) return "";
        return type.toLowerCase().replaceAll("\\s+", "");
    }

    private boolean isAcceptableTypeDiff(String exp, String act) {
        return exp.equals(act);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private DiffChange change(String table, String level, String name, String field,
                              String expected, String actual, DiffSeverity severity, String code) {
        return new DiffChange(table, level, name, field, expected, actual, severity, code);
    }
}
