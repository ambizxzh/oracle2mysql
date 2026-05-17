package io.o2m.migrate;

import io.o2m.core.config.MigrationConfig;
import io.o2m.core.util.IdentifierUtil;
import io.o2m.model.*;
import io.o2m.spi.MigrationPlanner;
import io.o2m.spi.SchemaComparator;

import java.util.ArrayList;
import java.util.List;

public class DefaultMigrationPlanner implements MigrationPlanner {
    private final SchemaComparator comparator;
    private final MigrationConfig migrationConfig;

    public DefaultMigrationPlanner(SchemaComparator comparator, MigrationConfig migrationConfig) {
        this.comparator = comparator;
        this.migrationConfig = migrationConfig;
    }

    @Override
    public MigrationPlan plan(SchemaSnapshot from, SchemaSnapshot to) {
        DiffResult diff = comparator.compare(from, to);
        List<MigrationChange> changes = new ArrayList<>();
        for (DiffChange c : diff.changes()) {
            if (c.severity() == DiffSeverity.ERROR && "TABLE_MISSING_EXPECTED".equals(c.code())) {
                to.findTable(c.table()).ifPresent(t -> changes.add(new MigrationChange(
                        t.name(), buildCreateTable(t), DiffSeverity.INFO)));
            } else if ("COLUMN_MISSING".equals(c.code())) {
                to.findTable(c.table()).flatMap(t -> t.findColumn(c.name())).ifPresent(col -> {
                    String sql = "ALTER TABLE `" + c.table().toLowerCase() + "` ADD COLUMN `"
                            + col.name().toLowerCase() + "` " + col.mysqlType()
                            + (col.nullable() ? "" : " NOT NULL") + ";";
                    changes.add(new MigrationChange(c.table(), sql, DiffSeverity.WARNING));
                });
            }
        }
        return new MigrationPlan(from.versionTag(), to.versionTag(), changes);
    }

    private String buildCreateTable(TableMetadata t) {
        return "-- Use full mysql.sql export for new table " + t.name();
    }
}
