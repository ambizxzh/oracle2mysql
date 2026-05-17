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
        loadPrimaryKey(schema, tableName, b);
        loadIndexes(schema, tableName, b);
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

    private void loadPrimaryKey(String schema, String table, TableMetadata.Builder b) throws SQLException {
        String sql = """
                SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                  ON t.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                WHERE t.TABLE_SCHEMA = ? AND t.TABLE_NAME = ? AND t.CONSTRAINT_TYPE = 'PRIMARY KEY'
                ORDER BY k.ORDINAL_POSITION
                """;
        Map<String, List<String>> pkCols = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkCols.computeIfAbsent(rs.getString("CONSTRAINT_NAME"), k -> new ArrayList<>())
                            .add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        pkCols.forEach((name, cols) -> b.primaryKey(new PrimaryKeyMetadata(name, cols)));
    }

    private void loadIndexes(String schema, String table, TableMetadata.Builder b) throws SQLException {
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
