package io.o2m.oracle;

import io.o2m.model.*;
import io.o2m.spi.MetadataCollector;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class OracleMetadataCollector implements MetadataCollector {
    private final DataSource dataSource;

    public OracleMetadataCollector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String sourceId() {
        return "oracle";
    }

    @Override
    public SchemaSnapshot collect(String schemaName, List<String> tableNames, String versionTag) throws Exception {
        String owner = schemaName.toUpperCase();
        List<String> tables = resolveTables(owner, tableNames);
        List<TableMetadata> result = new ArrayList<>();
        for (String table : tables) {
            result.add(collectTable(owner, table));
        }
        return new SchemaSnapshot("oracle", owner, versionTag, Instant.now(), result);
    }

    private List<String> resolveTables(String owner, List<String> tableNames) throws SQLException {
        if (tableNames != null && !tableNames.isEmpty()) {
            return tableNames.stream().map(String::toUpperCase).toList();
        }
        String sql = "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? ORDER BY TABLE_NAME";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            List<String> names = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
            return names;
        }
    }

    private TableMetadata collectTable(String owner, String tableName) throws SQLException {
        TableMetadata.Builder b = TableMetadata.builder(owner, tableName);
        loadTableComment(owner, tableName, b);
        loadColumns(owner, tableName, b);
        Set<String> constraintIndexNames = new HashSet<>();
        loadConstraints(owner, tableName, b, constraintIndexNames);
        loadIndexes(owner, tableName, b, constraintIndexNames);
        return b.build();
    }

    private void loadTableComment(String owner, String table, TableMetadata.Builder b) throws SQLException {
        String sql = "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER = ? AND TABLE_NAME = ? AND COMMENTS IS NOT NULL";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) b.comment(rs.getString(1));
            }
        }
    }

    private void loadColumns(String owner, String table, TableMetadata.Builder b) throws SQLException {
        String sql = """
                SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.CHAR_LENGTH,
                       c.DATA_PRECISION, c.DATA_SCALE, c.NULLABLE, c.DATA_DEFAULT,
                       cc.COMMENTS
                FROM ALL_TAB_COLUMNS c
                LEFT JOIN ALL_COL_COMMENTS cc ON c.OWNER = cc.OWNER AND c.TABLE_NAME = cc.TABLE_NAME
                    AND c.COLUMN_NAME = cc.COLUMN_NAME
                WHERE c.OWNER = ? AND c.TABLE_NAME = ?
                ORDER BY c.COLUMN_ID
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    boolean nullable = "Y".equals(rs.getString("NULLABLE"));
                    String def = rs.getString("DATA_DEFAULT");
                    if (def != null) def = def.trim();
                    b.column(ColumnMetadata.builder(colName)
                            .oracleType(rs.getString("DATA_TYPE"))
                            .dataLength(getInt(rs, "DATA_LENGTH"))
                            .charLength(getInt(rs, "CHAR_LENGTH"))
                            .dataPrecision(getInt(rs, "DATA_PRECISION"))
                            .dataScale(getInt(rs, "DATA_SCALE"))
                            .nullable(nullable)
                            .defaultValue(def)
                            .comment(rs.getString("COMMENTS"))
                            .build());
                }
            }
        }
    }

    private void loadConstraints(String owner, String table, TableMetadata.Builder b, Set<String> constraintIndexNames) throws SQLException {
        String sql = """
                SELECT c.CONSTRAINT_NAME, c.CONSTRAINT_TYPE, c.SEARCH_CONDITION, cc.COLUMN_NAME, cc.POSITION
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE IN ('P','U','R','C')
                ORDER BY c.CONSTRAINT_NAME, cc.POSITION
                """;
        Map<String, List<String>> colsByConstraint = new LinkedHashMap<>();
        Map<String, String> typeByConstraint = new HashMap<>();
        Map<String, String> searchByConstraint = new HashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cn = rs.getString("CONSTRAINT_NAME");
                    typeByConstraint.put(cn, rs.getString("CONSTRAINT_TYPE"));
                    searchByConstraint.putIfAbsent(cn, rs.getString("SEARCH_CONDITION"));
                    colsByConstraint.computeIfAbsent(cn, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        for (Map.Entry<String, String> e : typeByConstraint.entrySet()) {
            String cn = e.getKey();
            List<String> cols = colsByConstraint.get(cn);
            switch (e.getValue()) {
                case "P" -> {
                    b.primaryKey(new PrimaryKeyMetadata(cn, cols));
                    constraintIndexNames.add(cn);
                }
                case "U" -> {
                    b.uniqueKey(new UniqueKeyMetadata(cn, cols));
                    constraintIndexNames.add(cn);
                }
                case "C" -> {
                    String expr = searchByConstraint.get(cn);
                    boolean manual = expr != null && expr.length() > 200;
                    b.check(new CheckConstraintMetadata(cn, expr, manual));
                }
                default -> {}
            }
        }
        loadForeignKeys(owner, table, b);
    }

    private void loadForeignKeys(String owner, String table, TableMetadata.Builder b) throws SQLException {
        String sql = """
                SELECT c.CONSTRAINT_NAME, cc.COLUMN_NAME, cc.POSITION,
                       r.OWNER R_OWNER, r.TABLE_NAME R_TABLE, rcc.COLUMN_NAME R_COLUMN
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                JOIN ALL_CONSTRAINTS r ON c.R_OWNER = r.OWNER AND c.R_CONSTRAINT_NAME = r.CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS rcc ON r.OWNER = rcc.OWNER AND r.CONSTRAINT_NAME = rcc.CONSTRAINT_NAME
                    AND cc.POSITION = rcc.POSITION
                WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'R'
                ORDER BY c.CONSTRAINT_NAME, cc.POSITION
                """;
        Map<String, ForeignKeyBuilder> fks = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    ForeignKeyBuilder fb = fks.get(name);
                    if (fb == null) {
                        fb = new ForeignKeyBuilder(name, rs.getString("R_OWNER"), rs.getString("R_TABLE"));
                        fks.put(name, fb);
                    }
                    fb.columns.add(rs.getString("COLUMN_NAME"));
                    fb.refColumns.add(rs.getString("R_COLUMN"));
                }
            }
        }
        for (ForeignKeyBuilder fb : fks.values()) {
            b.foreignKey(new ForeignKeyMetadata(fb.name, fb.columns, fb.refSchema, fb.refTable, fb.refColumns, false));
        }
    }

    private void loadIndexes(String owner, String table, TableMetadata.Builder b, Set<String> constraintIndexNames) throws SQLException {
        String sql = """
                SELECT i.INDEX_NAME, i.UNIQUENESS, i.INDEX_TYPE, ic.COLUMN_NAME, ic.COLUMN_POSITION
                FROM ALL_INDEXES i
                JOIN ALL_IND_COLUMNS ic ON i.OWNER = ic.INDEX_OWNER AND i.INDEX_NAME = ic.INDEX_NAME
                WHERE i.TABLE_OWNER = ? AND i.TABLE_NAME = ? AND i.INDEX_TYPE NOT IN ('LOB')
                ORDER BY i.INDEX_NAME, ic.COLUMN_POSITION
                """;
        Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String in = rs.getString("INDEX_NAME");
                    IndexBuilder ib = indexes.get(in);
                    if (ib == null) {
                        ib = new IndexBuilder(in, "UNIQUE".equals(rs.getString("UNIQUENESS")), rs.getString("INDEX_TYPE"));
                        indexes.put(in, ib);
                    }
                    ib.columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        for (IndexBuilder ib : indexes.values()) {
            if (constraintIndexNames.contains(ib.name)) continue;
            boolean gap = ib.indexType != null && (ib.indexType.contains("BITMAP") || ib.indexType.contains("FUNCTION"));
            b.index(new IndexMetadata(ib.name, ib.columns, ib.unique, ib.indexType, gap));
        }
    }

    private static Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static class ForeignKeyBuilder {
        final String name;
        final String refSchema;
        final String refTable;
        final List<String> columns = new ArrayList<>();
        final List<String> refColumns = new ArrayList<>();

        ForeignKeyBuilder(String name, String refSchema, String refTable) {
            this.name = name;
            this.refSchema = refSchema;
            this.refTable = refTable;
        }
    }

    private static class IndexBuilder {
        final String name;
        final boolean unique;
        final String indexType;
        final List<String> columns = new ArrayList<>();

        IndexBuilder(String name, boolean unique, String indexType) {
            this.name = name;
            this.unique = unique;
            this.indexType = indexType;
        }
    }
}
