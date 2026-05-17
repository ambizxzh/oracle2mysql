package io.o2m.translate.coverage;

import io.o2m.model.*;
import io.o2m.spi.CoverageValidator;

import java.util.ArrayList;
import java.util.List;

public class TableCoverageChecker implements CoverageValidator {
    @Override
    public TableCoverageReport validate(TableMetadata table) {
        List<CoverageItem> items = new ArrayList<>();
        items.add(item("TABLE", "name", CoverageStatus.PRESENT, table.name()));

        if (table.comment() != null && !table.comment().isBlank()) {
            items.add(item("TABLE", "comment", CoverageStatus.PRESENT, table.comment()));
        }

        for (ColumnMetadata col : table.columns()) {
            items.add(item("COLUMN", col.name() + ".name", CoverageStatus.PRESENT, col.name()));
            if (col.mysqlType() == null || col.mysqlType().isBlank()) {
                items.add(item("COLUMN", col.name() + ".mysqlType", CoverageStatus.BLOCKER, "missing mysql type"));
            } else {
                items.add(item("COLUMN", col.name() + ".mysqlType", CoverageStatus.PRESENT, col.mysqlType()));
            }
            if (col.comment() != null && !col.comment().isBlank()) {
                items.add(item("COLUMN", col.name() + ".comment", CoverageStatus.PRESENT, col.comment()));
            }
            if (!col.nullable()) {
                items.add(item("COLUMN", col.name() + ".notNull", CoverageStatus.PRESENT, "NOT NULL"));
            }
        }

        if (table.primaryKey() != null) {
            items.add(item("CONSTRAINT", "PK", CoverageStatus.PRESENT, table.primaryKey().name()));
        }
        for (UniqueKeyMetadata uk : table.uniqueKeys()) {
            items.add(item("CONSTRAINT", "UK:" + uk.name(), CoverageStatus.PRESENT, String.join(",", uk.columns())));
        }
        for (ForeignKeyMetadata fk : table.foreignKeys()) {
            CoverageStatus st = fk.deferred() ? CoverageStatus.GAP : CoverageStatus.PRESENT;
            items.add(item("CONSTRAINT", "FK:" + fk.name(), st, fk.referencedTable()));
        }
        for (IndexMetadata idx : table.indexes()) {
            if (idx.gap()) {
                items.add(item("INDEX", idx.name(), CoverageStatus.GAP, idx.indexType()));
            } else {
                items.add(item("INDEX", idx.name(), CoverageStatus.PRESENT, String.join(",", idx.columns())));
            }
        }

        boolean passed = items.stream().noneMatch(i -> i.status() == CoverageStatus.BLOCKER || i.status() == CoverageStatus.MISSING);
        return new TableCoverageReport(table.name(), items, passed);
    }

    private CoverageItem item(String cat, String el, CoverageStatus status, String detail) {
        return new CoverageItem(cat, el, status, detail);
    }
}
