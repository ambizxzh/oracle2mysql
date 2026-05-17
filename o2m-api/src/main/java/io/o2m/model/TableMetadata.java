package io.o2m.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record TableMetadata(
        String schema,
        String name,
        String comment,
        List<ColumnMetadata> columns,
        PrimaryKeyMetadata primaryKey,
        List<UniqueKeyMetadata> uniqueKeys,
        List<ForeignKeyMetadata> foreignKeys,
        List<CheckConstraintMetadata> checks,
        List<IndexMetadata> indexes
) {
    public TableMetadata {
        columns = columns != null ? List.copyOf(columns) : List.of();
        uniqueKeys = uniqueKeys != null ? List.copyOf(uniqueKeys) : List.of();
        foreignKeys = foreignKeys != null ? List.copyOf(foreignKeys) : List.of();
        checks = checks != null ? List.copyOf(checks) : List.of();
        indexes = indexes != null ? List.copyOf(indexes) : List.of();
    }

    public boolean isPrimaryKeyColumn(String columnName) {
        return primaryKey != null && primaryKey.columns().contains(columnName);
    }

    public Optional<ColumnMetadata> findColumn(String columnName) {
        return columns.stream().filter(c -> c.name().equalsIgnoreCase(columnName)).findFirst();
    }

    public static Builder builder(String schema, String name) {
        return new Builder(schema, name);
    }

    public static final class Builder {
        private final String schema;
        private final String name;
        private String comment;
        private final List<ColumnMetadata> columns = new ArrayList<>();
        private PrimaryKeyMetadata primaryKey;
        private final List<UniqueKeyMetadata> uniqueKeys = new ArrayList<>();
        private final List<ForeignKeyMetadata> foreignKeys = new ArrayList<>();
        private final List<CheckConstraintMetadata> checks = new ArrayList<>();
        private final List<IndexMetadata> indexes = new ArrayList<>();

        public Builder(String schema, String name) {
            this.schema = schema;
            this.name = name;
        }

        public Builder comment(String v) { this.comment = v; return this; }
        public Builder column(ColumnMetadata c) { this.columns.add(c); return this; }
        public Builder primaryKey(PrimaryKeyMetadata pk) { this.primaryKey = pk; return this; }
        public Builder uniqueKey(UniqueKeyMetadata uk) { this.uniqueKeys.add(uk); return this; }
        public Builder foreignKey(ForeignKeyMetadata fk) { this.foreignKeys.add(fk); return this; }
        public Builder check(CheckConstraintMetadata c) { this.checks.add(c); return this; }
        public Builder index(IndexMetadata i) { this.indexes.add(i); return this; }

        public TableMetadata build() {
            return new TableMetadata(schema, name, comment, columns, primaryKey, uniqueKeys, foreignKeys, checks, indexes);
        }
    }
}
