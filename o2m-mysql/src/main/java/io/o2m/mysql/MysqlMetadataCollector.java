package io.o2m.mysql;

import io.o2m.model.*;
import io.o2m.spi.MetadataCollector;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class MysqlMetadataCollector implements MetadataCollector {
    private final DataSource dataSource;
    private final String database;

    public MysqlMetadataCollector(DataSource dataSource, String database) {
        this.dataSource = dataSource;
        this.database = database;
    }

    @Override
    public String sourceId() {
        return "mysql";
    }

    @Override
    public SchemaSnapshot collect(String schemaName, List<String> tableNames, String versionTag) throws Exception {
        List<String> tables = resolveTables(tableNames);
        List<TableMetadata> result = new ArrayList<>();
        for (String table : tables) {
            result.add(collectTable(database, table));
        }
        return new SchemaSnapshot("mysql", database, versionTag, Instant.now(), result);
    }

    private List<String> resolveTables(List<String> tableNames) throws SQLException {
        if (tableNames != null && !tableNames.isEmpty()) {
            return tableNames;
        }
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, database);
            List<String> names = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
            return names;
        }
    }

    private TableMetadata collectTable(String schema, String tableName) throws SQLException {
        TableMetadata.Builder b = TableMetadata.builder(schema, tableName);
        loadTableComment(schema, tableName, b);
        loadColumns(schema, tableName, b);
        Set<String> constraintIndexNames = new HashSet<>();
        loadPrimaryKey(schema, tableName, b, constraintIndexNames);
        loadUniqueKeys(schema, tableName, b, constraintIndexNames);
        loadForeignKeys(schema, tableName, b);
        loadChecks(schema, tableName, b);
        loadIndexes(schema, tableName, b, constraintIndexNames);
        return b.build();
    }

    private void loadTableComment(String schema, String table, TableMetadata.Builder b) throws SQLException {
        String sql = "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String comment = rs.getString(1);
                    if (comment != null && !comment.isBlank()) b.comment(comment);
                }
            }
        }
    }

    private void loadColumns(String schema, String table, TableMetadata.Builder b) throws SQLException {
        String sql = """
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    b.column(ColumnMetadata.builder(rs.getString("COLUMN_NAME"))
                            .mysqlType(rs.getString("COLUMN_TYPE"))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .defaultValue(rs.getString("COLUMN_DEFAULT"))
                            .comment(rs.getString("COLUMN_COMMENT"))
                            .oracleType("MYSQL")
                            .typeMappingReason(TypeMappingReason.MYSQL_NATIVE)
                            .build());
                }
            }
        }
    }

    private void loadKeyConstraints(String schema, String table, String constraintType,
                                    TableMetadata.Builder b, Set<String> constraintIndexNames,
                                    KeyConstraintConsumer consumer) throws SQLException {
        String sql = """
                SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                  ON t.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                 AND t.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                 AND t.TABLE_SCHEMA = k.TABLE_SCHEMA
                 AND t.TABLE_NAME = k.TABLE_NAME
                WHERE t.TABLE_SCHEMA = ? AND t.TABLE_NAME = ? AND t.CONSTRAINT_TYPE = ?
                ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION
                """;
        Map<String, List<String>> colsByName = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, constraintType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cn = rs.getString("CONSTRAINT_NAME");
                    colsByName.computeIfAbsent(cn, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                    constraintIndexNames.add(cn);
                }
            }
        }
        for (Map.Entry<String, List<String>> e : colsByName.entrySet()) {
            consumer.accept(e.getKey(), e.getValue());
        }
    }

    private void loadPrimaryKey(String schema, String table, TableMetadata.Builder b,
                                Set<String> constraintIndexNames) throws SQLException {
        loadKeyConstraints(schema, table, "PRIMARY KEY", b, constraintIndexNames,
                (name, cols) -> b.primaryKey(new PrimaryKeyMetadata(name, cols)));
    }

    private void loadUniqueKeys(String schema, String table, TableMetadata.Builder b,
                                Set<String> constraintIndexNames) throws SQLException {
        loadKeyConstraints(schema, table, "UNIQUE", b, constraintIndexNames,
                (name, cols) -> b.uniqueKey(new UniqueKeyMetadata(name, cols)));
    }

    private void loadForeignKeys(String schema, String table, TableMetadata.Builder b) throws SQLException {
        String sql = """
                SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.ORDINAL_POSITION,
                       k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                  ON t.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                 AND t.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                 AND t.TABLE_SCHEMA = k.TABLE_SCHEMA
                 AND t.TABLE_NAME = k.TABLE_NAME
                WHERE t.TABLE_SCHEMA = ? AND t.TABLE_NAME = ? AND t.CONSTRAINT_TYPE = 'FOREIGN KEY'
                ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION
                """;
        Map<String, FkBuilder> fks = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    FkBuilder fb = fks.get(name);
                    if (fb == null) {
                        fb = new FkBuilder(name, rs.getString("REFERENCED_TABLE_SCHEMA"),
                                rs.getString("REFERENCED_TABLE_NAME"));
                        fks.put(name, fb);
                    }
                    fb.columns.add(rs.getString("COLUMN_NAME"));
                    fb.refColumns.add(rs.getString("REFERENCED_COLUMN_NAME"));
                }
            }
        }
        for (FkBuilder fb : fks.values()) {
            b.foreignKey(new ForeignKeyMetadata(fb.name, fb.columns, fb.refSchema, fb.refTable, fb.refColumns, false));
        }
    }

    private void loadChecks(String schema, String table, TableMetadata.Builder b) throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ? AND tc.CONSTRAINT_TYPE = 'CHECK'
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    String expr = rs.getString("CHECK_CLAUSE");
                    b.check(new CheckConstraintMetadata(name, expr, false));
                }
            }
        }
    }

    private void loadIndexes(String schema, String table, TableMetadata.Builder b,
                            Set<String> constraintIndexNames) throws SQLException {
        String sql = """
                SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND INDEX_NAME != 'PRIMARY'
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """;
        Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String in = rs.getString("INDEX_NAME");
                    if (constraintIndexNames.contains(in)) continue;
                    IndexBuilder ib = indexes.get(in);
                    if (ib == null) {
                        ib = new IndexBuilder(in, rs.getInt("NON_UNIQUE") == 0);
                        indexes.put(in, ib);
                    }
                    ib.columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        indexes.values().forEach(ib -> b.index(new IndexMetadata(ib.name, ib.columns, ib.unique, "BTREE", false)));
    }

    @FunctionalInterface
    private interface KeyConstraintConsumer {
        void accept(String name, List<String> columns);
    }

    private static class FkBuilder {
        final String name;
        final String refSchema;
        final String refTable;
        final List<String> columns = new ArrayList<>();
        final List<String> refColumns = new ArrayList<>();

        FkBuilder(String name, String refSchema, String refTable) {
            this.name = name;
            this.refSchema = refSchema;
            this.refTable = refTable;
        }
    }

    private static class IndexBuilder {
        final String name;
        final boolean unique;
        final List<String> columns = new ArrayList<>();

        IndexBuilder(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }
}
