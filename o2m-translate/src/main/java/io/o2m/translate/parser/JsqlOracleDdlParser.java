package io.o2m.translate.parser;

import io.o2m.model.*;
import io.o2m.spi.DdlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.comment.Comment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class JsqlOracleDdlParser implements DdlParser {
    @Override
    public SchemaSnapshot parse(Path ddlFileOrDir, String schemaName, String versionTag) throws Exception {
        List<TableMetadata> tables = new ArrayList<>();
        String tableComment = null;
        try (Stream<Path> paths = Files.isDirectory(ddlFileOrDir)
                ? Files.walk(ddlFileOrDir).filter(p -> p.toString().endsWith(".sql"))
                : Stream.of(ddlFileOrDir)) {
            for (Path p : paths.toList()) {
                String content = Files.readString(p);
                for (String stmt : splitStatements(content)) {
                    Object parsed = parseStatement(stmt.trim());
                    if (parsed instanceof CreateTable ct) {
                        tables.add(parseCreateTable(ct, schemaName));
                    } else if (parsed instanceof Comment comment) {
                        applyComment(tables, comment);
                    }
                }
            }
        }
        return new SchemaSnapshot("ddl-import", schemaName, versionTag, Instant.now(), tables);
    }

    @Override
    public SchemaSnapshot parseString(String ddl, String schemaName, String tableName) throws Exception {
        CreateTable ct = (CreateTable) CCJSqlParserUtil.parse(ddl);
        return new SchemaSnapshot("ddl-import", schemaName, null, Instant.now(),
                List.of(parseCreateTable(ct, schemaName)));
    }

    private Object parseStatement(String stmt) throws Exception {
        if (stmt.isBlank()) return null;
        Statement s = CCJSqlParserUtil.parse(stmt);
        return s;
    }

    private TableMetadata parseCreateTable(CreateTable ct, String schema) {
        Table t = ct.getTable();
        String tableName = t.getName().replace("\"", "");
        TableMetadata.Builder b = TableMetadata.builder(schema, tableName);
        if (ct.getColumnDefinitions() != null) {
            for (ColumnDefinition cd : ct.getColumnDefinitions()) {
                String colName = cd.getColumnName().replace("\"", "");
                String colSpec = cd.getColDataType().toString();
                b.column(parseColumn(colName, colSpec, cd));
            }
        }
        if (ct.getIndexes() != null) {
            for (Index idx : ct.getIndexes()) {
                String type = idx.getType();
                if (type != null && type.toUpperCase().contains("PRIMARY KEY")) {
                    List<String> cols = idx.getColumnsNames().stream()
                            .map(c -> c.replace("\"", "")).toList();
                    b.primaryKey(new PrimaryKeyMetadata("PK_" + tableName, cols));
                }
            }
        }
        return b.build();
    }

    private ColumnMetadata parseColumn(String name, String colSpec, ColumnDefinition cd) {
        String upper = colSpec.toUpperCase();
        Integer precision = null, scale = null, length = null;
        String baseType = upper;
        int paren = upper.indexOf('(');
        if (paren > 0) {
            baseType = upper.substring(0, paren);
            String inside = upper.substring(paren + 1, upper.indexOf(')'));
            String[] parts = inside.split(",");
            if (parts.length == 2) {
                precision = Integer.parseInt(parts[0].trim());
                scale = Integer.parseInt(parts[1].trim());
            } else {
                length = Integer.parseInt(parts[0].trim());
            }
        }
        boolean nullable = cd.getColumnSpecs() == null || cd.getColumnSpecs().stream()
                .noneMatch(s -> "NOT".equalsIgnoreCase(s) || "NULL".equalsIgnoreCase(s));
        return ColumnMetadata.builder(name)
                .oracleType(baseType)
                .dataPrecision(precision)
                .dataScale(scale)
                .charLength(length)
                .dataLength(length)
                .nullable(nullable)
                .build();
    }

    private void applyComment(List<TableMetadata> tables, Comment comment) {
        String text = comment.getComment() != null ? stripQuotes(comment.getComment().getValue()) : null;
        if (text == null || text.isBlank()) return;

        if (comment.getTable() != null) {
            String tableName = comment.getTable().getName().replace("\"", "");
            for (int i = 0; i < tables.size(); i++) {
                TableMetadata t = tables.get(i);
                if (t.name().equalsIgnoreCase(tableName)) {
                    tables.set(i, new TableMetadata(t.schema(), t.name(), text,
                            t.columns(), t.primaryKey(), t.uniqueKeys(),
                            t.foreignKeys(), t.checks(), t.indexes()));
                    break;
                }
            }
        } else if (comment.getColumn() != null) {
            String rawCol = comment.getColumn().getColumnName().replace("\"", "");
            String tableName = null;
            String colName = rawCol;

            if (comment.getColumn().getTable() != null) {
                tableName = comment.getColumn().getTable().getName().replace("\"", "");
            } else if (rawCol.contains(".")) {
                String[] parts = rawCol.split("\\.");
                if (parts.length >= 2) {
                    tableName = parts[parts.length - 2];
                    colName = parts[parts.length - 1];
                }
            }
            if (tableName == null) return;

            for (int i = 0; i < tables.size(); i++) {
                TableMetadata t = tables.get(i);
                if (t.name().equalsIgnoreCase(tableName)) {
                    List<ColumnMetadata> newCols = new ArrayList<>();
                    for (ColumnMetadata col : t.columns()) {
                        if (col.name().equalsIgnoreCase(colName)) {
                            newCols.add(new ColumnMetadata(col.name(), col.oracleType(), col.dataLength(),
                                    col.charLength(), col.dataPrecision(), col.dataScale(), col.nullable(),
                                    col.defaultValue(), text, col.mysqlType(), col.typeMappingReason(),
                                    col.manualReview()));
                        } else {
                            newCols.add(col);
                        }
                    }
                    tables.set(i, new TableMetadata(t.schema(), t.name(), t.comment(),
                            newCols, t.primaryKey(), t.uniqueKeys(), t.foreignKeys(), t.checks(), t.indexes()));
                    break;
                }
            }
        }
    }

    private String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("'") && s.endsWith("'")) return s.substring(1, s.length() - 1);
        return s;
    }

    private List<String> splitStatements(String content) {
        List<String> stmts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : content.split("\n")) {
            cur.append(line).append("\n");
            if (line.trim().endsWith(";")) {
                stmts.add(cur.toString());
                cur = new StringBuilder();
            }
        }
        if (cur.length() > 0 && !cur.toString().isBlank()) stmts.add(cur.toString());
        return stmts;
    }
}
