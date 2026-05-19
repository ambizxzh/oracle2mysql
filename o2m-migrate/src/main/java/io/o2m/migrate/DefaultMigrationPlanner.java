package io.o2m.migrate;

import io.o2m.core.config.MigrationConfig;
import io.o2m.core.config.RulesConfig;
import io.o2m.core.util.DependencySortUtil;
import io.o2m.core.util.IdentifierUtil;
import io.o2m.model.*;
import io.o2m.spi.MigrationPlanner;
import io.o2m.spi.MysqlDdlGenerator;
import io.o2m.spi.SchemaComparator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultMigrationPlanner implements MigrationPlanner {
    private final SchemaComparator comparator;
    private final MigrationConfig migrationConfig;
    private final RulesConfig rules;
    private final MysqlDdlGenerator ddlGenerator;

    public DefaultMigrationPlanner(SchemaComparator comparator, MigrationConfig migrationConfig,
                                   RulesConfig rules, MysqlDdlGenerator ddlGenerator) {
        this.comparator = comparator;
        this.migrationConfig = migrationConfig;
        this.rules = rules;
        this.ddlGenerator = ddlGenerator;
    }

    @Override
    public MigrationPlan plan(SchemaSnapshot from, SchemaSnapshot to) {
        // Compare target (to=expected) against current (from=actual) so that
        // TABLE_MISSING_EXPECTED means "target has it, current does not" => CREATE TABLE.
        DiffResult diff = comparator.compare(to, from);
        List<MigrationChange> changes = new ArrayList<>();
        Map<String, TableMetadata> missingTables = new LinkedHashMap<>();

        // First pass: collect all missing tables
        for (DiffChange c : diff.changes()) {
            if ("TABLE_MISSING_EXPECTED".equals(c.code())) {
                to.findTable(c.table()).ifPresent(t -> missingTables.put(t.name().toUpperCase(), t));
            }
        }

        // Generate CREATE TABLE statements first, sorted by FK dependency
        if (!missingTables.isEmpty()) {
            List<TableMetadata> sorted = DependencySortUtil.sortTables(new ArrayList<>(missingTables.values()));
            for (TableMetadata t : sorted) {
                GeneratedDdl ddl = ddlGenerator.generate(t);
                changes.add(new MigrationChange(t.name(), ddl.mysqlDdl(), DiffSeverity.INFO));
            }
        }

        // Second pass: handle all other changes (ALTER TABLE, etc.)
        for (DiffChange c : diff.changes()) {
            switch (c.code()) {
                case "TABLE_MISSING_EXPECTED" -> { /* already handled above */ }
                case "TABLE_MISSING_ACTUAL" -> handleTableMissingActual(c, changes);
                case "COLUMN_MISSING" -> handleColumnMissing(c, to, changes);
                case "TYPE_DIFF", "NULLABLE_DIFF", "COLUMN_COMMENT_DIFF" -> handleColumnModify(c, to, changes);
                case "TABLE_COMMENT_DIFF" -> handleTableCommentDiff(c, to, changes);
                case "PK_MISSING", "PK_COLUMNS_DIFF" -> handlePrimaryKeyDiff(c, to, changes);
                case "UK_MISSING" -> handleUniqueKeyMissing(c, to, changes);
                case "FK_MISSING" -> handleForeignKeyMissing(c, to, changes);
                case "CHECK_MISSING" -> handleCheckMissing(c, to, changes);
                case "INDEX_MISSING", "INDEX_COLUMNS_DIFF" -> handleIndexDiff(c, to, changes);
                default -> {
                    // PK_NAME_DIFF, UK_NAME_DIFF, FK_NAME_DIFF: name-only diffs, no structural change needed
                }
            }
        }
        return new MigrationPlan(from.versionTag(), to.versionTag(), changes);
    }

    private void handleTableMissingExpected(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).ifPresent(t -> {
            GeneratedDdl ddl = ddlGenerator.generate(t);
            changes.add(new MigrationChange(t.name(), ddl.mysqlDdl(), DiffSeverity.INFO));
        });
    }

    private void handleTableMissingActual(DiffChange c, List<MigrationChange> changes) {
        if (migrationConfig.isAllowDestructive()) {
            String sql = "DROP TABLE IF EXISTS " + toId(c.table()) + ";";
            changes.add(new MigrationChange(c.table(), sql, DiffSeverity.ERROR));
        }
    }

    private void handleColumnMissing(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).flatMap(t -> t.findColumn(c.name())).ifPresent(col -> {
            String sql = buildAddColumn(c.table(), col);
            changes.add(new MigrationChange(c.table(), sql, DiffSeverity.WARNING));
        });
    }

    private void handleColumnModify(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).flatMap(t -> t.findColumn(c.name())).ifPresent(col -> {
            String sql = buildModifyColumn(c.table(), col);
            changes.add(new MigrationChange(c.table(), sql, DiffSeverity.WARNING));
        });
    }

    private void handleTableCommentDiff(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).ifPresent(t -> {
            if (t.comment() != null && !t.comment().isBlank()) {
                String sql = "ALTER TABLE " + toId(t.name())
                        + " COMMENT = '" + IdentifierUtil.escapeComment(t.comment()) + "';";
                changes.add(new MigrationChange(t.name(), sql, DiffSeverity.INFO));
            }
        });
    }

    private void handlePrimaryKeyDiff(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).map(TableMetadata::primaryKey).filter(Objects::nonNull).ifPresent(pk -> {
            StringBuilder sql = new StringBuilder();
            if ("PK_COLUMNS_DIFF".equals(c.code())) {
                sql.append("ALTER TABLE ").append(toId(c.table())).append(" DROP PRIMARY KEY;\n");
            }
            String pkName = IdentifierUtil.safeConstraintName(pk.name(), 64);
            sql.append("ALTER TABLE ").append(toId(c.table()))
                    .append(" ADD CONSTRAINT ").append(toId(pkName))
                    .append(" PRIMARY KEY (").append(joinCols(pk.columns())).append(");");
            changes.add(new MigrationChange(c.table(), sql.toString(), DiffSeverity.ERROR));
        });
    }

    private void handleUniqueKeyMissing(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).flatMap(t -> t.uniqueKeys().stream()
                .filter(uk -> uk.name().equalsIgnoreCase(c.name())).findFirst()).ifPresent(uk -> {
            String ukName = IdentifierUtil.safeConstraintName(uk.name(), 64);
            String sql = "ALTER TABLE " + toId(c.table())
                    + " ADD CONSTRAINT " + toId(ukName)
                    + " UNIQUE (" + joinCols(uk.columns()) + ");";
            changes.add(new MigrationChange(c.table(), sql, DiffSeverity.ERROR));
        });
    }

    private void handleForeignKeyMissing(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).flatMap(t -> t.foreignKeys().stream()
                .filter(fk -> fk.name().equalsIgnoreCase(c.name()) && !fk.deferred()).findFirst()).ifPresent(fk -> {
            String fkName = IdentifierUtil.safeConstraintName(fk.name(), 64);
            String sql = "ALTER TABLE " + toId(c.table())
                    + " ADD CONSTRAINT " + toId(fkName)
                    + " FOREIGN KEY (" + joinCols(fk.columns()) + ")"
                    + " REFERENCES " + toId(fk.referencedTable())
                    + " (" + joinCols(fk.referencedColumns()) + ");";
            changes.add(new MigrationChange(c.table(), sql, DiffSeverity.ERROR));
        });
    }

    private void handleCheckMissing(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).flatMap(t -> t.checks().stream()
                .filter(chk -> chk.name().equalsIgnoreCase(c.name())
                        && !chk.manualReview()
                        && chk.expression() != null).findFirst()).ifPresent(chk -> {
            String chkName = IdentifierUtil.safeConstraintName(chk.name(), 64);
            String sql = "ALTER TABLE " + toId(c.table())
                    + " ADD CONSTRAINT " + toId(chkName)
                    + " CHECK (" + chk.expression() + ");";
            changes.add(new MigrationChange(c.table(), sql, DiffSeverity.ERROR));
        });
    }

    private void handleIndexDiff(DiffChange c, SchemaSnapshot to, List<MigrationChange> changes) {
        to.findTable(c.table()).flatMap(t -> t.indexes().stream()
                .filter(i -> c.name().equalsIgnoreCase(IdentifierUtil.mysqlConstraintName(i.name(), rules)))
                .filter(i -> !i.gap()).findFirst()).ifPresent(idx -> {
            StringBuilder sql = new StringBuilder();
            String idxName = IdentifierUtil.toMysql(IdentifierUtil.safeConstraintName(idx.name(), 64), rules);
            if ("INDEX_COLUMNS_DIFF".equals(c.code())) {
                sql.append("DROP INDEX ").append(idxName).append(" ON ").append(toId(c.table())).append(";\n");
            }
            sql.append(idx.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                    .append(idxName).append(" ON ").append(toId(c.table()))
                    .append(" (").append(joinCols(idx.columns())).append(");");
            changes.add(new MigrationChange(c.table(), sql.toString(), DiffSeverity.ERROR));
        });
    }

    private String buildAddColumn(String tableName, ColumnMetadata col) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(toId(tableName))
                .append(" ADD COLUMN ").append(toId(col.name()))
                .append(" ").append(col.mysqlType() != null ? col.mysqlType() : "LONGTEXT");
        if (!col.nullable()) sql.append(" NOT NULL");
        String def = mapDefault(col);
        if (def != null) sql.append(" DEFAULT ").append(def);
        if (col.commentOptional().isPresent()) {
            sql.append(" COMMENT '").append(IdentifierUtil.escapeComment(col.comment())).append("'");
        }
        sql.append(";");
        return sql.toString();
    }

    private String buildModifyColumn(String tableName, ColumnMetadata col) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(toId(tableName))
                .append(" MODIFY COLUMN ").append(toId(col.name()))
                .append(" ").append(col.mysqlType() != null ? col.mysqlType() : "LONGTEXT");
        if (!col.nullable()) sql.append(" NOT NULL");
        String def = mapDefault(col);
        if (def != null) sql.append(" DEFAULT ").append(def);
        if (col.commentOptional().isPresent()) {
            sql.append(" COMMENT '").append(IdentifierUtil.escapeComment(col.comment())).append("'");
        }
        sql.append(";");
        return sql.toString();
    }

    private String toId(String name) {
        return IdentifierUtil.toMysql(name, rules);
    }

    private String joinCols(List<String> cols) {
        return String.join(", ", cols.stream().map(this::toId).toList());
    }

    private String mapDefault(ColumnMetadata col) {
        if (col.defaultValue() == null || col.defaultValue().isBlank()) return null;
        String d = col.defaultValue().trim();
        if (d.toUpperCase().contains("SYSDATE")) return "CURRENT_TIMESTAMP";
        return d;
    }
}
