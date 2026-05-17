package io.o2m.translate.ddl;

import io.o2m.core.util.IdentifierUtil;
import io.o2m.model.*;
import io.o2m.spi.OracleDdlGenerator;

public class DefaultOracleDdlGenerator implements OracleDdlGenerator {
    @Override
    public GeneratedDdl generate(TableMetadata table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(IdentifierUtil.toOracle(table.name())).append(" (\n");
        for (int i = 0; i < table.columns().size(); i++) {
            ColumnMetadata col = table.columns().get(i);
            if (i > 0) sb.append(",\n");
            sb.append("  ").append(IdentifierUtil.toOracle(col.name())).append("  ")
                    .append(formatOracleType(col));
            if (!col.nullable()) sb.append(" NOT NULL");
            if (col.defaultValue() != null && !col.defaultValue().isBlank()) {
                sb.append(" DEFAULT ").append(col.defaultValue());
            }
        }
        if (table.primaryKey() != null) {
            sb.append(",\n  CONSTRAINT ").append(table.primaryKey().name())
                    .append(" PRIMARY KEY (").append(String.join(", ", table.primaryKey().columns().stream()
                            .map(IdentifierUtil::toOracle).toList())).append(")");
        }
        for (UniqueKeyMetadata uk : table.uniqueKeys()) {
            sb.append(",\n  CONSTRAINT ").append(uk.name()).append(" UNIQUE (")
                    .append(String.join(", ", uk.columns().stream().map(IdentifierUtil::toOracle).toList())).append(")");
        }
        sb.append("\n);\n");
        if (table.comment() != null && !table.comment().isBlank()) {
            sb.append("COMMENT ON TABLE ").append(IdentifierUtil.toOracle(table.name()))
                    .append(" IS '").append(IdentifierUtil.escapeComment(table.comment())).append("';\n");
        }
        for (ColumnMetadata col : table.columns()) {
            if (col.comment() != null && !col.comment().isBlank()) {
                sb.append("COMMENT ON COLUMN ").append(IdentifierUtil.toOracle(table.name())).append(".")
                        .append(IdentifierUtil.toOracle(col.name()))
                        .append(" IS '").append(IdentifierUtil.escapeComment(col.comment())).append("';\n");
            }
        }
        for (IndexMetadata idx : table.indexes()) {
            if (idx.gap()) continue;
            sb.append(idx.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                    .append(idx.name()).append(" ON ")
                    .append(IdentifierUtil.toOracle(table.name())).append(" (")
                    .append(String.join(", ", idx.columns().stream().map(IdentifierUtil::toOracle).toList()))
                    .append(");\n");
        }
        return new GeneratedDdl(table.name(), sb.toString().trim(), null);
    }

    private String formatOracleType(ColumnMetadata col) {
        String t = col.oracleType();
        if (!"NUMBER".equalsIgnoreCase(t)) {
            if (col.charLength() != null) return t + "(" + col.charLength() + ")";
            if (col.dataLength() != null && t.toUpperCase().startsWith("VARCHAR")) return t + "(" + col.dataLength() + ")";
            return t;
        }
        if (col.dataPrecision() == null && col.dataScale() == null) return "NUMBER";
        if (col.dataScale() == null || col.dataScale() == 0) {
            return col.dataPrecision() != null ? "NUMBER(" + col.dataPrecision() + ",0)" : "NUMBER";
        }
        return "NUMBER(" + col.dataPrecision() + "," + col.dataScale() + ")";
    }
}
