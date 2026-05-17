package io.o2m.translate.ddl;

import io.o2m.core.config.RulesConfig;
import io.o2m.core.util.IdentifierUtil;
import io.o2m.model.*;
import io.o2m.spi.MysqlDdlGenerator;

public class DefaultMysqlDdlGenerator implements MysqlDdlGenerator {
    private final RulesConfig rules;

    public DefaultMysqlDdlGenerator(RulesConfig rules) {
        this.rules = rules;
    }

    @Override
    public GeneratedDdl generate(TableMetadata table) {
        StringBuilder sb = new StringBuilder();
        String tableId = IdentifierUtil.toMysql(table.name(), rules);
        sb.append("CREATE TABLE ").append(tableId).append(" (\n");
        for (int i = 0; i < table.columns().size(); i++) {
            ColumnMetadata col = table.columns().get(i);
            if (i > 0) sb.append(",\n");
            sb.append("  ").append(IdentifierUtil.toMysql(col.name(), rules)).append("  ")
                    .append(col.mysqlType() != null ? col.mysqlType() : "LONGTEXT");
            if (!col.nullable()) sb.append(" NOT NULL");
            String def = mapDefault(col);
            if (def != null) sb.append(" DEFAULT ").append(def);
            if (col.commentOptional().isPresent()) {
                sb.append(" COMMENT '").append(IdentifierUtil.escapeComment(col.comment())).append("'");
            }
        }
        if (table.primaryKey() != null) {
            String pkName = IdentifierUtil.safeConstraintName(table.primaryKey().name(), 64);
            sb.append(",\n  CONSTRAINT ").append(IdentifierUtil.toMysql(pkName, rules))
                    .append(" PRIMARY KEY (").append(joinCols(table.primaryKey().columns())).append(")");
        }
        for (UniqueKeyMetadata uk : table.uniqueKeys()) {
            String ukName = IdentifierUtil.safeConstraintName(uk.name(), 64);
            sb.append(",\n  CONSTRAINT ").append(IdentifierUtil.toMysql(ukName, rules))
                    .append(" UNIQUE (").append(joinCols(uk.columns())).append(")");
        }
        for (ForeignKeyMetadata fk : table.foreignKeys()) {
            if (fk.deferred()) {
                sb.append("\n  -- FK_DEFERRED: ").append(fk.name());
                continue;
            }
            String fkName = IdentifierUtil.safeConstraintName(fk.name(), 64);
            sb.append(",\n  CONSTRAINT ").append(IdentifierUtil.toMysql(fkName, rules))
                    .append(" FOREIGN KEY (").append(joinCols(fk.columns())).append(")")
                    .append(" REFERENCES ").append(IdentifierUtil.toMysql(fk.referencedTable(), rules))
                    .append(" (").append(joinCols(fk.referencedColumns())).append(")");
        }
        for (CheckConstraintMetadata chk : table.checks()) {
            if (chk.manualReview() || chk.expression() == null) continue;
            String chkName = IdentifierUtil.safeConstraintName(chk.name(), 64);
            sb.append(",\n  CONSTRAINT ").append(IdentifierUtil.toMysql(chkName, rules))
                    .append(" CHECK (").append(chk.expression()).append(")");
        }
        sb.append("\n) ENGINE=").append(rules.getEngine())
                .append(" DEFAULT CHARSET=").append(rules.getCharset())
                .append(" COLLATE=").append(rules.getCollation());
        if (table.comment() != null && !table.comment().isBlank()) {
            sb.append("\n  COMMENT='").append(IdentifierUtil.escapeComment(table.comment())).append("'");
        }
        sb.append(";\n");

        for (IndexMetadata idx : table.indexes()) {
            if (idx.gap()) {
                sb.append("-- GAP_INDEX: ").append(idx.name()).append(" type=").append(idx.indexType()).append("\n");
                continue;
            }
            String idxName = IdentifierUtil.toMysql(IdentifierUtil.safeConstraintName(idx.name(), 64), rules);
            sb.append(idx.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                    .append(idxName).append(" ON ").append(tableId).append(" (")
                    .append(joinCols(idx.columns())).append(");\n");
        }
        return new GeneratedDdl(table.name(), null, sb.toString().trim());
    }

    private String joinCols(java.util.List<String> cols) {
        return String.join(", ", cols.stream().map(c -> IdentifierUtil.toMysql(c, rules)).toList());
    }

    private String mapDefault(ColumnMetadata col) {
        if (col.defaultValue() == null || col.defaultValue().isBlank()) return null;
        String d = col.defaultValue().trim();
        if (d.toUpperCase().contains("SYSDATE")) return "CURRENT_TIMESTAMP";
        return d;
    }
}
